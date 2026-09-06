package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.CountryOperatingMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class WorkerClassificationResolver {

    private final JdbcTemplate jdbc;

    public WorkerClassificationResolver(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public String resolve(UUID tenantId, UUID employmentId, CountryOperatingMode mode) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(mode, "mode");

        if (mode == CountryOperatingMode.GLOBAL) {
            return "GENERIC_EMPLOYEE";
        }

        List<String> values = jdbc.query(
                "SELECT worker_classification_code FROM hr_employees WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> rs.getString(1), tenantId, employmentId);
        if (values.isEmpty()) {
            throw new IllegalStateException("HRM_LEGAL_REVIEW_REQUIRED: employment not found for worker classification");
        }
        String value = values.get(0);
        return value == null || value.isBlank() ? "GENERIC_EMPLOYEE" : value;
    }
}
