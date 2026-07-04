package com.sanad.platform.health.service;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.health.api.HealthDtos.DataPressureResponse;
import com.sanad.platform.health.api.HealthDtos.HealthActionDescriptor;
import com.sanad.platform.health.api.HealthDtos.HealthActionRequest;
import com.sanad.platform.health.api.HealthDtos.HealthActionResult;
import com.sanad.platform.health.api.HealthDtos.PlatformHealthResponse;
import com.sanad.platform.health.api.HealthDtos.RiskForecastPoint;
import com.sanad.platform.health.api.HealthDtos.RuntimeMetricsResponse;
import com.sanad.platform.health.api.HealthDtos.ServiceHealthResponse;
import com.sanad.platform.health.api.HealthDtos.TenantHealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executive health aggregation, risk prediction and allow-listed self-healing. */
@Service
public class HealthIntelligenceService {

    private static final Set<String> SCOPES = Set.of("PLATFORM", "SERVICE", "TENANT");
    private static final Set<String> ACTIONS = Set.of(
            "RUN_DIAGNOSTICS", "AUTO_HEAL", "MARK_MAINTENANCE",
            "RESTORE_OPERATION", "REFRESH_TENANT_HEALTH");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;

    public HealthIntelligenceService(JdbcTemplate jdbcTemplate, PlatformAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PlatformHealthResponse snapshot() {
        RuntimeMetricsResponse runtime = runtimeMetrics();
        DataPressureResponse dataPressure = dataPressure(runtime);
        List<ServiceHealthResponse> services = serviceHealth();
        List<TenantHealthResponse> tenants = tenantHealth();
        int serviceScore = average(services.stream().map(ServiceHealthResponse::healthScore).toList(), 100);
        int tenantScore = average(tenants.stream().map(TenantHealthResponse::healthScore).toList(), 100);
        int runtimeScore = clamp((int) Math.round(
                100 - Math.max(runtime.cpuLoadPercent(), runtime.memoryUsagePercent()) * 0.55));
        int healthScore = clamp((serviceScore * 45 + tenantScore * 30 + runtimeScore * 15
                + (100 - dataPressure.pressureScore()) * 10) / 100);
        String riskLevel = riskLevel(100 - healthScore);
        String status = healthScore >= 85 ? "HEALTHY" : healthScore >= 65 ? "DEGRADED" : "CRITICAL";
        int pressureTrend = Math.max(dataPressure.pressureScore(),
                services.stream().mapToInt(ServiceHealthResponse::pressureScore).max().orElse(0));
        return new PlatformHealthResponse(
                Instant.now(), status, healthScore, riskLevel,
                predictionSummary(healthScore, pressureTrend), runtime, dataPressure,
                services, tenants, forecast(healthScore, pressureTrend), availableActions());
    }

    @Transactional
    public HealthActionResult execute(HealthActionRequest request, Authentication authentication) {
        String scope = normalize(request.scope());
        String action = normalize(request.action());
        if (!SCOPES.contains(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported health action scope");
        }
        if (!ACTIONS.contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported health action");
        }
        validateTarget(scope, request.targetId());
        validateAction(scope, action);
        PlatformHealthResponse before = snapshot();
        String message = switch (action) {
            case "RUN_DIAGNOSTICS" -> runDiagnostics(scope, request.targetId());
            case "AUTO_HEAL" -> autoHeal(scope, request.targetId());
            case "MARK_MAINTENANCE" -> updateServiceState(
                    request.targetId(), "MAINTENANCE", null,
                    "Maintenance set from executive health console");
            case "RESTORE_OPERATION" -> restoreService(request.targetId());
            case "REFRESH_TENANT_HEALTH" -> "Tenant health signals recalculated successfully";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported health action");
        };
        PlatformHealthResponse after = snapshot();
        auditService.success(
                authentication,
                "TENANT".equals(scope) ? request.targetId() : null,
                "HEALTH.ACTION." + action,
                "HEALTH_" + scope,
                request.targetId() == null ? "PLATFORM" : request.targetId().toString(),
                request.reason(),
                Map.of("healthScore", before.healthScore(), "status", before.overallStatus()),
                Map.of("healthScore", after.healthScore(), "status", after.overallStatus(), "message", message)
        );
        return new HealthActionResult(action, scope, request.targetId(), "SUCCESS", message, Instant.now(), after);
    }

    private RuntimeMetricsResponse runtimeMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long maxBytes = heap.getMax() > 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
        long usedBytes = Math.max(0, heap.getUsed());
        double cpuPercent = 0;
        java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        if (base instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double load = operatingSystem.getCpuLoad();
            if (load >= 0) cpuPercent = load * 100;
        }
        return new RuntimeMetricsResponse(
                rounded(cpuPercent), rounded(percent(usedBytes, maxBytes)),
                usedBytes / (1024 * 1024), maxBytes / (1024 * 1024),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                Runtime.getRuntime().availableProcessors());
    }

    private DataPressureResponse dataPressure(RuntimeMetricsResponse runtime) {
        long trackedRows = count("SELECT COUNT(*) FROM users")
                + count("SELECT COUNT(*) FROM organizations")
                + count("SELECT COUNT(*) FROM organization_memberships")
                + count("SELECT COUNT(*) FROM billing_invoices")
                + count("SELECT COUNT(*) FROM platform_audit_logs");
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long auditEvents = count("SELECT COUNT(*) FROM platform_audit_logs WHERE created_at >= ?", oneHourAgo);
        long failedEvents = count(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE created_at >= ? AND result = 'FAILURE'", oneHourAgo);
        long openInvoices = count("SELECT COUNT(*) FROM billing_invoices WHERE status = 'OPEN'");
        long activeUsers = count("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'");
        int rowPressure = clamp((int) Math.round(Math.min(100, trackedRows / 5000.0 * 100)));
        int auditPressure = clamp((int) Math.round(Math.min(100, auditEvents / 500.0 * 100)));
        int failurePressure = clamp((int) Math.round(Math.min(100, failedEvents / 20.0 * 100)));
        int runtimePressure = clamp((int) Math.round(
                Math.max(runtime.cpuLoadPercent(), runtime.memoryUsagePercent())));
        int score = clamp((rowPressure * 25 + auditPressure * 20
                + failurePressure * 25 + runtimePressure * 30) / 100);
        String status = score < 55 ? "NORMAL" : score < 75 ? "ELEVATED" : score < 90 ? "HIGH" : "CRITICAL";
        String message = score < 55
                ? "ضغط البيانات والعمليات ضمن الحدود الطبيعية"
                : score < 75
                ? "يوصى بمراقبة نمو السجلات ومعدل الأحداث"
                : "يتطلب الضغط الحالي تدخلاً تشغيلياً وتوسعة السعة";
        return new DataPressureResponse(score, status, trackedRows, auditEvents,
                failedEvents, openInvoices, activeUsers, message);
    }

    private List<ServiceHealthResponse> serviceHealth() {
        return jdbcTemplate.query(
                "SELECT id, code, name, environment, status, criticality, last_checked_at, "
                        + "last_latency_ms, last_message FROM system_services ORDER BY criticality DESC, code",
                this::mapServiceHealth);
    }

    private ServiceHealthResponse mapServiceHealth(ResultSet resultSet, int rowNumber) throws SQLException {
        String status = resultSet.getString("status");
        String criticality = resultSet.getString("criticality");
        Object latencyValue = resultSet.getObject("last_latency_ms");
        Long latency = latencyValue == null ? null : ((Number) latencyValue).longValue();
        int statusPenalty = switch (status) {
            case "OPERATIONAL" -> 0;
            case "MAINTENANCE" -> 20;
            case "DEGRADED" -> 35;
            case "DISABLED" -> 45;
            default -> 65;
        };
        int latencyPressure = latency == null ? 8
                : clamp((int) Math.round(Math.min(100, latency / 20.0)));
        int criticalityPressure = switch (criticality) {
            case "CRITICAL" -> 15;
            case "HIGH" -> 10;
            case "MEDIUM" -> 5;
            default -> 0;
        };
        int pressure = clamp(latencyPressure + criticalityPressure + statusPenalty / 2);
        int health = clamp(100 - statusPenalty - latencyPressure / 3);
        String predicted = pressure >= 80 ? "INCIDENT_RISK"
                : pressure >= 60 ? "DEGRADATION_RISK" : "STABLE";
        return new ServiceHealthResponse(
                resultSet.getObject("id", UUID.class), resultSet.getString("code"),
                resultSet.getString("name"), resultSet.getString("environment"),
                status, criticality, health, pressure, riskLevel(100 - health), latency,
                resultSet.getString("last_message"), instant(resultSet, "last_checked_at"), predicted);
    }

    private List<TenantHealthResponse> tenantHealth() {
        return jdbcTemplate.query(
                "SELECT t.id, t.name, t.status, "
                        + "COUNT(DISTINCT u.id) AS users_count, "
                        + "COUNT(DISTINCT o.id) AS organizations_count, "
                        + "COUNT(DISTINCT m.id) AS memberships_count, "
                        + "COUNT(DISTINCT bi.id) AS invoices_count, "
                        + "COUNT(DISTINCT CASE WHEN bi.status = 'OPEN' THEN bi.id END) AS open_invoices_count, "
                        + "COALESCE(MAX(ts.seat_quantity), 0) AS seat_capacity "
                        + "FROM tenants t "
                        + "LEFT JOIN users u ON u.tenant_id = t.id "
                        + "LEFT JOIN organizations o ON o.tenant_id = t.id "
                        + "LEFT JOIN organization_memberships m ON m.tenant_id = t.id "
                        + "LEFT JOIN billing_invoices bi ON bi.tenant_id = t.id "
                        + "LEFT JOIN tenant_subscriptions ts ON ts.tenant_id = t.id "
                        + "GROUP BY t.id, t.name, t.status ORDER BY t.name",
                this::mapTenantHealth);
    }

    private TenantHealthResponse mapTenantHealth(ResultSet resultSet, int rowNumber) throws SQLException {
        long users = resultSet.getLong("users_count");
        long organizations = resultSet.getLong("organizations_count");
        long memberships = resultSet.getLong("memberships_count");
        long invoices = resultSet.getLong("invoices_count");
        long openInvoices = resultSet.getLong("open_invoices_count");
        long seatCapacity = resultSet.getLong("seat_capacity");
        long records = users + organizations + memberships + invoices;
        int seatUtilization = seatCapacity <= 0 ? (users > 0 ? 100 : 0)
                : clamp((int) Math.round(percent(users, seatCapacity)));
        int volumePressure = clamp((int) Math.round(Math.min(100, records / 1000.0 * 100)));
        int pressure = clamp((seatUtilization * 65 + volumePressure * 35) / 100);
        String tenantStatus = resultSet.getString("status");
        int lifecyclePenalty = switch (tenantStatus) {
            case "ACTIVE" -> 0;
            case "TRIAL" -> 5;
            case "PENDING" -> 12;
            case "PAST_DUE" -> 25;
            case "SUSPENDED" -> 40;
            default -> 55;
        };
        int health = clamp(100 - lifecyclePenalty - Math.max(0, pressure - 70) / 2
                - (int) Math.min(20, openInvoices * 4));
        String prediction = pressure >= 90 ? "احتمال اختناق مرتفع خلال ساعة"
                : pressure >= 75 ? "قد يحتاج إلى زيادة السعة قريباً"
                : pressure >= 55 ? "نمو ملحوظ يحتاج متابعة"
                : "مستقر ضمن السعة الحالية";
        return new TenantHealthResponse(
                resultSet.getObject("id", UUID.class), resultSet.getString("name"), tenantStatus,
                health, pressure, riskLevel(100 - health), users, organizations, memberships,
                invoices, openInvoices, seatCapacity, seatUtilization, records, prediction);
    }

    private String runDiagnostics(String scope, UUID targetId) {
        long started = System.nanoTime();
        Integer databaseProbe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        if (databaseProbe == null || databaseProbe != 1) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database diagnostic failed");
        }
        if ("SERVICE".equals(scope)) {
            jdbcTemplate.update(
                    "UPDATE system_services SET last_checked_at = ?, last_latency_ms = ?, "
                            + "last_message = ?, updated_at = ? WHERE id = ?",
                    Instant.now(), latencyMs, "Controlled diagnostic completed", Instant.now(), targetId);
        }
        return "Controlled diagnostics completed; database latency " + latencyMs + " ms";
    }

    private String autoHeal(String scope, UUID targetId) {
        if ("SERVICE".equals(scope)) return restoreService(targetId);
        long started = System.nanoTime();
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        RuntimeMetricsResponse runtime = runtimeMetrics();
        String apiStatus = runtime.memoryUsagePercent() >= 92 || runtime.cpuLoadPercent() >= 92
                ? "DEGRADED" : "OPERATIONAL";
        jdbcTemplate.update(
                "UPDATE system_services SET status = 'OPERATIONAL', last_checked_at = ?, "
                        + "last_latency_ms = ?, last_message = ?, updated_at = ? WHERE code = 'DATABASE'",
                Instant.now(), latencyMs, "Database probe passed during platform auto-heal", Instant.now());
        jdbcTemplate.update(
                "UPDATE system_services SET status = ?, last_checked_at = ?, last_latency_ms = ?, "
                        + "last_message = ?, updated_at = ? WHERE code = 'API'",
                apiStatus, Instant.now(), latencyMs,
                "Runtime and database checks completed during platform auto-heal", Instant.now());
        return "Platform auto-heal completed; API status " + apiStatus + ", database operational";
    }

    private String restoreService(UUID serviceId) {
        ServiceTarget target = serviceTarget(serviceId);
        if (!Set.of("API", "DATABASE").contains(target.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Automatic restoration is available only for API and DATABASE; use diagnostics for other services");
        }
        long started = System.nanoTime();
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        RuntimeMetricsResponse runtime = runtimeMetrics();
        if ("API".equals(target.code())
                && (runtime.memoryUsagePercent() >= 95 || runtime.cpuLoadPercent() >= 95)) {
            return updateServiceState(serviceId, "DEGRADED", latencyMs,
                    "Restoration blocked by critical runtime pressure");
        }
        return updateServiceState(serviceId, "OPERATIONAL", latencyMs,
                "Service restored after controlled health verification");
    }

    private String updateServiceState(UUID serviceId, String status, Long latencyMs, String message) {
        int updated = jdbcTemplate.update(
                "UPDATE system_services SET status = ?, last_checked_at = ?, last_latency_ms = ?, "
                        + "last_message = ?, updated_at = ? WHERE id = ?",
                status, Instant.now(), latencyMs, message, Instant.now(), serviceId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System service not found");
        }
        return message;
    }

    private void validateTarget(String scope, UUID targetId) {
        if ("PLATFORM".equals(scope)) {
            if (targetId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Platform actions do not accept targetId");
            }
            return;
        }
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetId is required for this scope");
        }
        String table = "SERVICE".equals(scope) ? "system_services" : "tenants";
        if (count("SELECT COUNT(*) FROM " + table + " WHERE id = ?", targetId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, scope + " target not found");
        }
    }

    private void validateAction(String scope, String action) {
        boolean valid = switch (scope) {
            case "PLATFORM" -> Set.of("RUN_DIAGNOSTICS", "AUTO_HEAL").contains(action);
            case "SERVICE" -> Set.of("RUN_DIAGNOSTICS", "AUTO_HEAL",
                    "MARK_MAINTENANCE", "RESTORE_OPERATION").contains(action);
            case "TENANT" -> Set.of("RUN_DIAGNOSTICS", "REFRESH_TENANT_HEALTH").contains(action);
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Action is not allowed for the selected scope");
        }
    }

    private ServiceTarget serviceTarget(UUID serviceId) {
        List<ServiceTarget> targets = jdbcTemplate.query(
                "SELECT id, code FROM system_services WHERE id = ?",
                (resultSet, rowNumber) -> new ServiceTarget(
                        resultSet.getObject("id", UUID.class), resultSet.getString("code")),
                serviceId);
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System service not found");
        }
        return targets.get(0);
    }

    private List<RiskForecastPoint> forecast(int healthScore, int pressureTrend) {
        int baseRisk = clamp(100 - healthScore);
        int slope = pressureTrend < 55 ? 0 : pressureTrend < 75 ? 4 : pressureTrend < 90 ? 8 : 14;
        return List.of(
                forecastPoint(0, baseRisk, "الآن"),
                forecastPoint(15, baseRisk + slope, "بعد 15 دقيقة"),
                forecastPoint(30, baseRisk + slope * 2, "بعد 30 دقيقة"),
                forecastPoint(60, baseRisk + slope * 3, "بعد ساعة"));
    }

    private RiskForecastPoint forecastPoint(int minutes, int score, String label) {
        int normalized = clamp(score);
        return new RiskForecastPoint(minutes, normalized, riskLevel(normalized), label);
    }

    private List<HealthActionDescriptor> availableActions() {
        return List.of(
                new HealthActionDescriptor("RUN_DIAGNOSTICS", "PLATFORM|SERVICE|TENANT",
                        "تشغيل التشخيص", "فحص آمن للاتصال وقابلية الاستجابة دون أوامر نظام عشوائية", false),
                new HealthActionDescriptor("AUTO_HEAL", "PLATFORM|SERVICE",
                        "التصحيح الذاتي", "تشخيص واستعادة آمنة لخدمات API وقاعدة البيانات", false),
                new HealthActionDescriptor("MARK_MAINTENANCE", "SERVICE",
                        "وضع الصيانة", "تحويل خدمة مسجلة إلى وضع الصيانة مع توثيق السبب", true),
                new HealthActionDescriptor("RESTORE_OPERATION", "SERVICE",
                        "استعادة التشغيل", "إعادة الخدمة إلى التشغيل بعد تحقق صحي فعلي", true),
                new HealthActionDescriptor("REFRESH_TENANT_HEALTH", "TENANT",
                        "إعادة تقييم المستأجر", "إعادة حساب مؤشرات صحة وضغط مستأجر محدد", true));
    }

    private String predictionSummary(int healthScore, int pressureTrend) {
        if (healthScore < 60 || pressureTrend >= 90) {
            return "توقع خطر مرتفع: قد يحدث تدهور تشغيلي خلال الساعة القادمة ما لم يتم تخفيف الضغط.";
        }
        if (healthScore < 80 || pressureTrend >= 70) {
            return "توقع متوسط: توجد إشارات ضغط متصاعدة تستوجب المتابعة والتشخيص الاستباقي.";
        }
        return "توقع مستقر: لا توجد مؤشرات حالية على اختناق أو تدهور قريب.";
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private static int average(List<Integer> values, int fallback) {
        if (values.isEmpty()) return fallback;
        return clamp((int) Math.round(
                values.stream().mapToInt(Integer::intValue).average().orElse(fallback)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double percent(long value, long total) {
        return total <= 0 ? 0 : (value * 100.0) / total;
    }

    private static double rounded(double value) {
        return Math.round(Math.max(0, Math.min(100, value)) * 10.0) / 10.0;
    }

    private static String riskLevel(int riskScore) {
        return riskScore < 25 ? "LOW" : riskScore < 50 ? "MEDIUM"
                : riskScore < 75 ? "HIGH" : "CRITICAL";
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value.getClass());
    }

    private record ServiceTarget(UUID id, String code) {
    }
}
