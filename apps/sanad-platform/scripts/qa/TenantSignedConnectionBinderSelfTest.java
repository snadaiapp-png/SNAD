package com.sanad.platform.security.rls;

import java.lang.reflect.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free JDBC protocol contract probes, runnable with javac/java 21. */
public final class TenantSignedConnectionBinderSelfTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final byte[] KEY = "unit-test-key-1234567890-abcdefghijk".getBytes(StandardCharsets.UTF_8);
    private static int passed;

    static final class Fixture {
        final List<String> queries = new ArrayList<>();
        final String txid = "98765";
        final int pid = 42;
        boolean autoCommit;
        boolean bindAccepted = true;
        boolean validationMatches = true;
        boolean slotPresent = true;
        String mac;
        String uuid;

        Connection connection() {
            return proxy(Connection.class, (obj, method, args) -> switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "createStatement" -> statement();
                case "prepareStatement" -> {
                    queries.add((String) args[0]);
                    yield preparedStatement();
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException("Connection." + method.getName());
            });
        }

        Statement statement() {
            return proxy(Statement.class, (obj, method, args) -> switch (method.getName()) {
                case "executeQuery" -> {
                    String sql = (String) args[0];
                    queries.add(sql);
                    if (sql.contains("app_tenant_ctx_slot")) {
                        yield resultSet("slot");
                    }
                    if (sql.contains("app_tenant_ctx_text")) {
                        yield resultSet("validation");
                    }
                    throw new AssertionError("Unexpected SQL: " + sql);
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException("Statement." + method.getName());
            });
        }

        PreparedStatement preparedStatement() {
            return proxy(PreparedStatement.class, (obj, method, args) -> switch (method.getName()) {
                case "setString" -> {
                    if ((int) args[0] == 1) uuid = (String) args[1];
                    else if ((int) args[0] == 2) mac = (String) args[1];
                    else throw new AssertionError("Unexpected param");
                    yield null;
                }
                case "executeQuery" -> resultSet("bind");
                case "close" -> null;
                default -> throw new UnsupportedOperationException("PreparedStatement." + method.getName());
            });
        }

        ResultSet resultSet(String kind) {
            return proxy(ResultSet.class, new InvocationHandler() {
                boolean advanced;
                @Override public Object invoke(Object obj, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> {
                            if (advanced) yield false;
                            advanced = true;
                            yield !"slot".equals(kind) || slotPresent;
                        }
                        case "getString" -> {
                            if ("slot".equals(kind)) yield txid;
                            if ("validation".equals(kind)) yield validationMatches ? TENANT.toString() : null;
                            throw new UnsupportedOperationException("ResultSet.getString on " + kind);
                        }
                        case "getInt" -> pid;
                        case "getBoolean" -> bindAccepted;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException("ResultSet." + method.getName());
                    };
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler fn) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, fn);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void expectThrows(Class<? extends Throwable> type, Runnable f, String label) {
        try { f.run(); } catch (Throwable x) {
            if (!type.isInstance(x)) throw new AssertionError(label + ": wrong error " + x, x);
            passed++; return;
        }
        throw new AssertionError(label + ": expected " + type.getSimpleName());
    }
    private static void bind(Fixture f) {
        try { TenantSignedConnectionBinder.bind(f.connection(), TENANT); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) {
        TenantContextSigner signer = new TenantContextSigner(KEY, false);
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(signer, true));
        try {
            Fixture happy = new Fixture();
            bind(happy);
            check(TENANT.toString().equals(happy.uuid), "Tenant parameter must bind as UUID string");
            check(signer.macHex(TENANT, happy.txid, happy.pid).equals(happy.mac), "HMAC must match transaction slot");
            check(happy.queries.size() == 3, "Protocol must execute exactly three statements");
            check(happy.queries.stream().noneMatch(s -> s.contains("set_config") || s.contains("app.ctx_mac")),
                    "Raw tenant GUC must never be issued");
            check(TENANT.equals(TenantRlsSignedBinding.currentTenant()), "Thread marker only after verified bind");
            TenantSignedConnectionBinder.clearThreadBinding();
            check(TenantRlsSignedBinding.currentTenant() == null, "Manual transaction cleanup required");
            passed++;

            Fixture autoc = new Fixture(); autoc.autoCommit = true;
            expectThrows(RuntimeException.class, () -> bind(autoc), "autocommit rejected");
            check(autoc.queries.isEmpty(), "No protocol SQL in autocommit");
            check(TenantRlsSignedBinding.currentTenant() == null, "autocommit leaves no marker");

            Fixture rejected = new Fixture(); rejected.bindAccepted = false;
            expectThrows(RuntimeException.class, () -> bind(rejected), "DB-rejected HMAC fails closed");
            check(TenantRlsSignedBinding.currentTenant() == null, "Rejected MAC leaves no marker");

            Fixture invalid = new Fixture(); invalid.validationMatches = false;
            expectThrows(RuntimeException.class, () -> bind(invalid), "DB validator mismatch fails closed");
            check(TenantRlsSignedBinding.currentTenant() == null, "Validator mismatch leaves no marker");

            Fixture noSlot = new Fixture(); noSlot.slotPresent = false;
            expectThrows(RuntimeException.class, () -> bind(noSlot), "Missing slot fails closed");
            check(TenantRlsSignedBinding.currentTenant() == null, "Missing slot leaves no marker");

            TenantRlsRuntimePolicy.reset();
            Fixture noKey = new Fixture();
            expectThrows(RuntimeException.class, () -> bind(noKey), "Missing HMAC key fails closed");
            check(noKey.queries.isEmpty(), "Key check precedes SQL");
            check(TenantRlsSignedBinding.currentTenant() == null, "Missing key leaves no marker");
        } finally {
            TenantRlsSignedBinding.clear();
            TenantRlsRuntimePolicy.reset();
        }
        System.out.println("PASS " + passed + "/6 isolated signed-connection protocol checks");
    }
}
