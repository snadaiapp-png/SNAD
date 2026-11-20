package com.sanad.platform.security.rls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dynamic-proxy invocation handler that lazily applies
 * {@code SET LOCAL app.tenant_id = '<uuid>'} to the underlying physical
 * connection the first time a statement is created within a transaction.
 *
 * <p>The {@code SET LOCAL} scoping ensures the GUC is scoped to the current
 * transaction and automatically resets on commit/rollback — safe for
 * connection-pool reuse.</p>
 *
 * <h3>Activation conditions</h3>
 * <ul>
 *   <li>{@code autoCommit == false} — we are inside a Spring
 *       {@code @Transactional} boundary (the primary CRM data-access path).</li>
 *   <li>A tenant id is present in the {@link SecurityContextHolder}.</li>
 * </ul>
 *
 * <h3>Pre-auth classification (no tenant in security context)</h3>
 * <ul>
 *   <li><b>Trusted scope present</b> — the transaction already carries a
 *       non-empty {@code app.tenant_id} GUC applied by an explicitly
 *       classified privileged path ({@code TenantRlsTransactionContext}
 *       callers: control-plane bootstrap, tenant-resolver login, per-tenant
 *       system workers). The handler stays hands-off: the GUC is the single
 *       source of truth for the classification.</li>
 *   <li><b>Data modification without any classification</b> — statements
 *       that mutate tenant data (INSERT/UPDATE/DELETE/MERGE, including
 *       writing CTEs) fail closed at the driver with
 *       {@code RLS_PRE_AUTH_UNCLASSIFIED} instead of silently issuing
 *       unscoped writes. PostgreSQL FORCE RLS would reject them anyway
 *       (42501); surfacing the rejection at the driver keeps the failure
 *       explicit and auditable rather than silent.</li>
 *   <li><b>Reads and untyped statements</b> — pass through unchanged;
 *       FORCE RLS fail-closed policies already reduce cross-tenant reads to
 *       zero rows when the GUC is absent. DDL executed by migrations via
 *       plain {@code Statement} is untouched.</li>
 * </ul>
 */
final class TenantRlsConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private UUID tenantAppliedFor = null;

    TenantRlsConnectionHandler(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        // Intercept statement-creation entry points to guarantee the GUC is
        // set before any SQL reaches the engine, and that unclassified
        // pre-auth writes fail closed before reaching the engine.
        if (isStatementCreation(name)) {
            ensureTenantContext(sqlArgument(args));
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            // Reflective JDBC delegation must preserve checked SQLException,
            // rather than exposing UndeclaredThrowableException to callers.
            throw exception.getCause();
        } finally {
            // SET LOCAL ends at commit/rollback. A pooled logical connection
            // may participate in another transaction after either boundary.
            if ("commit".equals(name) || "rollback".equals(name)
                    || "close".equals(name) || "setAutoCommit".equals(name)) {
                tenantAppliedFor = null;
            }
        }
    }

    private boolean isStatementCreation(String methodName) {
        return "createStatement".equals(methodName)
                || "prepareStatement".equals(methodName)
                || "prepareCall".equals(methodName)
                || "nativeSQL".equals(methodName);
    }

    private void ensureTenantContext(String sql) throws SQLException {
        // Only apply within an explicit transaction — SET LOCAL has no effect
        // (and would error) in autocommit mode.
        if (delegate.getAutoCommit()) {
            return;
        }
        UUID tenantId = currentTenantId();
        if (tenantAppliedFor != null) {
            // A DIFFERENT non-null tenant is a hard violation: the actor swapped
            // identity inside one transaction. A CLEARED context (null) is not an
            // identity change: the previously applied SET LOCAL GUC still scopes
            // every statement of this transaction to the original tenant, so RLS
            // remains fail-closed. Deferred flushes and post-authentication
            // verification queries therefore continue under the applied scope.
            if (tenantId != null && !tenantAppliedFor.equals(tenantId)) {
                throw new SQLException("Tenant identity changed inside an active RLS transaction");
            }
            return;
        }
        if (tenantId == null) {
            classifyPreAuthTransaction(sql);
            return;
        }
        try (var stmt = delegate.createStatement()) {
            // Parameterised SET LOCAL is not supported by PostgreSQL for GUC
            // assignment, but the value is a well-formed UUID read directly
            // from the validated JWT via the security context — never user-
            // supplied raw input at this layer.
            stmt.execute("SET LOCAL app.tenant_id = '" + tenantId.toString() + "'");
        }
        tenantAppliedFor = tenantId;
    }

    /**
     * Explicitly classifies a pre-auth transaction (no tenant in the security
     * context). The PostgreSQL catalog is the source of truth: a statement
     * that mutates a FORCE-RLS-protected table must either run under a
     * trusted tenant GUC (applied by a classified privileged path) or fail
     * closed at the driver. Writes to tables without row security (system
     * tables, non-tenant audit/tenant-directory rows) are untouched, and
     * PostgreSQL FORCE RLS remains the enforcement backstop in every case.
     */
    private void classifyPreAuthTransaction(String sql) throws SQLException {
        if (!isDataModification(sql)) {
            // Reads, DDL and untyped Statement entries pass through; the
            // fail-closed FORCE RLS policies govern actual row visibility.
            return;
        }
        String targetTable = targetTable(sql);
        if (targetTable != null && !isForceRlsProtected(targetTable)) {
            // Not tenant-protected data — no RLS classification required.
            return;
        }
        if (trustedPreAuthScopeApplied()) {
            // Classified privileged pre-auth path (bootstrap, tenant-resolver
            // login, per-tenant system worker): the transaction already carries
            // the trusted GUC — nothing to apply, nothing to hide.
            return;
        }
        throw new SQLException("RLS_PRE_AUTH_UNCLASSIFIED: refusing data-modification statement"
                + (targetTable != null ? " on '" + targetTable + "'" : "")
                + " inside a transaction that has no authenticated tenant context and no"
                + " trusted pre-auth tenant GUC (app.tenant_id is unset). Explicitly classify"
                + " privileged pre-auth paths by applying the trusted tenant GUC via"
                + " TenantRlsTransactionContext before writing tenant data.");
    }

    /**
     * @return true when this transaction already carries a non-empty trusted
     *         {@code app.tenant_id} GUC applied by a classified pre-auth path.
     */
    private boolean trustedPreAuthScopeApplied() throws SQLException {
        try (var stmt = delegate.createStatement();
             var rs = stmt.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
            if (rs.next()) {
                String value = rs.getString(1);
                return value != null && !value.isBlank();
            }
            return false;
        }
    }

    /**
     * @return true when the given table (optionally schema-qualified) is
     *         protected by both ENABLE and FORCE row-level security, per the
     *         live PostgreSQL catalog. Unknown tables resolve to false — the
     *         database itself then governs any statement that reaches it.
     */
    private boolean isForceRlsProtected(String table) throws SQLException {
        String sql = "SELECT c.relrowsecurity AND c.relforcerowsecurity"
                + " FROM pg_class c WHERE c.oid = to_regclass(?)";
        try (var ps = delegate.prepareStatement(sql)) {
            ps.setString(1, table);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    Boolean protectedTable = rs.getObject(1, Boolean.class);
                    return Boolean.TRUE.equals(protectedTable);
                }
                return false;
            }
        }
    }

    /**
     * Conservative mutation classifier for driver-side fail-closed gating of
     * unclassified pre-auth transactions. False negatives (a write that slips
     * through) remain protected by PostgreSQL FORCE RLS; false positives
     * (a read classified as a write) only fail toward the safer direction.
     */
    private static boolean isDataModification(String sql) {
        if (sql == null) {
            // createStatement()/prepareCall() without SQL text: the statement
            // type is unknowable at this layer — pass through unchanged.
            return false;
        }
        String stripped = stripCommentsAndWhitespace(sql);
        if (stripped.isEmpty()) {
            return false;
        }
        // Mutation detection is case-insensitive: Hibernate and Flyway emit
        // lowercase verbs ("insert into ..."), psql/DDL uppercase.
        String upper = stripped.toUpperCase(java.util.Locale.ROOT);
        if (startsWithAny(upper, "INSERT", "UPDATE", "DELETE", "MERGE", "CALL", "DO", "TRUNCATE")) {
            return true;
        }
        if (upper.startsWith("WITH")) {
            // Writing CTEs (e.g. WITH x AS (...) INSERT INTO ...) mutate data.
            return upper.contains("INSERT") || upper.contains("UPDATE")
                    || upper.contains("DELETE") || upper.contains("MERGE");
        }
        return false;
    }

    private static final java.util.regex.Pattern TARGET_TABLE_PATTERN =
            java.util.regex.Pattern.compile(
                    "\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|MERGE\\s+INTO)\\s+"
                            + "((?:\"[^\"]+\"|[A-Za-z0-9_$]+)(?:\\.(?:\"[^\"]+\"|[A-Za-z0-9_$]+))?)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the mutated table from the statement text (first write verb
     * occurrence, covering writing CTEs and quoted identifiers as emitted by
     * JDBC tooling such as Flyway). Returns null when no write target can be
     * identified — callers then fall back to the GUC/classification check
     * instead of the catalog check.
     */
    private static String targetTable(String sql) {
        String stripped = stripCommentsAndWhitespace(sql);
        var matcher = TARGET_TABLE_PATTERN.matcher(stripped);
        if (matcher.find()) {
            return matcher.group(1).replace("\"", "")
                    .toLowerCase(java.util.Locale.ROOT);
        }
        return null;
    }

    private static String stripCommentsAndWhitespace(String sql) {
        String noBlockComments = sql.replaceAll("/\\*.*?\\*/", " ");
        String noLineComments = noBlockComments.replaceAll("--[^\\n]*", " ");
        return noLineComments.stripLeading();
    }

    private static boolean startsWithAny(String upper, String... keywords) {
        for (String keyword : keywords) {
            if (upper.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String sqlArgument(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String sql)) {
            return null;
        }
        return sql;
    }

    /**
     * Read the tenant id from the Spring security context.
     *
     * <p>Mirrors the extraction logic in {@code SpringTenantContextAdapter}
     * and {@code CrmContractController#requiredTenant} — the tenant id is
     * placed into {@code Authentication.getDetails()} by
     * {@code JwtAuthenticationFilter} after JWT validation.</p>
     */
    private static UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (!(auth.getDetails() instanceof java.util.Map<?, ?> details)) {
            return null;
        }
        Object raw = details.get("tenant_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
