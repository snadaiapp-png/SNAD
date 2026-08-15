package com.sanad.platform.management;

import com.sanad.platform.management.application.GovernanceConfigurationDefaults;
import com.sanad.platform.management.application.GovernanceConfigurationService;
import com.sanad.platform.management.api.GovernanceConfigDtos.ConfigurationResponse;
import com.sanad.platform.management.api.GovernanceConfigDtos.ConfigType;
import com.sanad.platform.management.api.GovernanceConfigDtos.CreateConfigurationRequest;
import com.sanad.platform.management.api.GovernanceConfigDtos.ResolvedValue;
import com.sanad.platform.management.api.GovernanceConfigDtos.UpdateConfigurationRequest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for GAP 26 — Governance Configuration.
 *
 * Verifies the tenant-scoped runtime-editable governance configuration
 * store with safe-default fallback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class GovernanceConfigurationIntegrationTest {

    @Autowired private GovernanceConfigurationService configService;
    @Autowired private GovernanceConfigurationDefaults defaults;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "gc-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createConfiguration_persistsWithDefaults() {
        var req = new CreateConfigurationRequest(
                "sla.decision.due.default", "PT240H", ConfigType.DURATION_ISO, "test");
        ConfigurationResponse created = configService.create(tenantId, req, null);
        assertThat(created.configKey()).isEqualTo("sla.decision.due.default");
        assertThat(created.configValue()).isEqualTo("PT240H");
        assertThat(created.configType()).isEqualTo(ConfigType.DURATION_ISO);
        assertThat(created.enabled()).isTrue();
        assertThat(created.version()).isZero();
    }

    @Test
    void createConfiguration_rejectsInvalidIntegerValue() {
        var req = new CreateConfigurationRequest(
                "test.threshold", "not-an-int", ConfigType.INTEGER, null);
        assertThatThrownBy(() -> configService.create(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createConfiguration_rejectsInvalidBooleanValue() {
        var req = new CreateConfigurationRequest(
                "test.flag", "maybe", ConfigType.BOOLEAN, null);
        assertThatThrownBy(() -> configService.create(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createConfiguration_rejectsInvalidDurationValue() {
        var req = new CreateConfigurationRequest(
                "test.duration", "not-a-duration", ConfigType.DURATION_ISO, null);
        assertThatThrownBy(() -> configService.create(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createConfiguration_rejectsBlankKey() {
        var req = new CreateConfigurationRequest(
                "  ", "value", ConfigType.STRING, null);
        assertThatThrownBy(() -> configService.create(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createConfiguration_rejectsDuplicateKeyForSameTenant() {
        var req = new CreateConfigurationRequest(
                "test.dup.key", "v1", ConfigType.STRING, null);
        configService.create(tenantId, req, null);
        assertThatThrownBy(() -> configService.create(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listConfigurations_returnsOnlyTenantScopedRows() {
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "gc-other-" + otherTenant.toString().substring(0, 8), now, now);

        configService.create(tenantId,
                new CreateConfigurationRequest("key.a", "v-a", ConfigType.STRING, null), null);
        configService.create(otherTenant,
                new CreateConfigurationRequest("key.a", "v-other", ConfigType.STRING, null), null);

        var mine = configService.list(tenantId);
        var others = configService.list(otherTenant);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).configValue()).isEqualTo("v-a");
        assertThat(others).hasSize(1);
        assertThat(others.get(0).configValue()).isEqualTo("v-other");
    }

    @Test
    void updateConfiguration_changesValueAndIncrementsVersion() {
        ConfigurationResponse created = configService.create(tenantId,
                new CreateConfigurationRequest("test.update", "v1", ConfigType.STRING, null), null);
        ConfigurationResponse updated = configService.update(tenantId, created.id(),
                new UpdateConfigurationRequest("v2", null), null);
        assertThat(updated.configValue()).isEqualTo("v2");
        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void updateConfiguration_disableThenEnableTogglesFlag() {
        ConfigurationResponse created = configService.create(tenantId,
                new CreateConfigurationRequest("test.toggle", "v1", ConfigType.STRING, null), null);
        configService.update(tenantId, created.id(),
                new UpdateConfigurationRequest(null, false), null);
        assertThat(configService.get(tenantId, created.id()).enabled()).isFalse();
        configService.update(tenantId, created.id(),
                new UpdateConfigurationRequest(null, true), null);
        assertThat(configService.get(tenantId, created.id()).enabled()).isTrue();
    }

    @Test
    void deleteConfiguration_removesRow() {
        ConfigurationResponse created = configService.create(tenantId,
                new CreateConfigurationRequest("test.delete", "v1", ConfigType.STRING, null), null);
        configService.delete(tenantId, created.id(), null);
        assertThatThrownBy(() -> configService.get(tenantId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolveInteger_fallsBackToDefaultWhenMissing() {
        ResolvedValue<Integer> resolved = configService.resolveInteger(
                tenantId, "alert.dedup.window.seconds", 999);
        assertThat(resolved.value()).isEqualTo(300);   // registered default
        assertThat(resolved.fromTenantOverride()).isFalse();
        assertThat(resolved.source()).isEqualTo("REGISTERED_DEFAULT");
    }

    @Test
    void resolveInteger_usesTenantOverrideWhenPresent() {
        configService.create(tenantId,
                new CreateConfigurationRequest("alert.dedup.window.seconds", "600",
                        ConfigType.INTEGER, "override"), null);
        ResolvedValue<Integer> resolved = configService.resolveInteger(
                tenantId, "alert.dedup.window.seconds", 999);
        assertThat(resolved.value()).isEqualTo(600);
        assertThat(resolved.fromTenantOverride()).isTrue();
        assertThat(resolved.source()).isEqualTo("TENANT_OVERRIDE");
    }

    @Test
    void resolveDuration_fallsBackToRegisteredDefault() {
        ResolvedValue<Duration> resolved = configService.resolveDuration(
                tenantId, "sla.decision.due.default", Duration.ofHours(72));
        assertThat(resolved.value()).isEqualTo(Duration.ofHours(168));
        assertThat(resolved.source()).isEqualTo("REGISTERED_DEFAULT");
    }

    @Test
    void resolveBoolean_fallsBackToRegisteredDefault() {
        ResolvedValue<Boolean> resolved = configService.resolveBoolean(
                tenantId, "governance.sod.enforce.self_approval_block", false);
        assertThat(resolved.value()).isTrue();
        assertThat(resolved.source()).isEqualTo("REGISTERED_DEFAULT");
    }

    @Test
    void resolveString_fallsBackToCallerDefaultWhenNoRegistered() {
        ResolvedValue<String> resolved = configService.resolveString(
                tenantId, "unknown.key", "fallback");
        assertThat(resolved.value()).isEqualTo("fallback");
        assertThat(resolved.source()).isEqualTo("CALLER_DEFAULT");
    }

    @Test
    void defaults_allDefaultsMapIsNotEmpty() {
        assertThat(defaults.allDefaults()).isNotEmpty();
        assertThat(defaults.allDefaults()).containsKey("sla.decision.due.default");
    }

    @Test
    void getConfiguration_rejectsCrossTenantAccess() {
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "gc-x-tenant-" + otherTenant.toString().substring(0, 8), now, now);

        ConfigurationResponse created = configService.create(tenantId,
                new CreateConfigurationRequest("test.cross", "v1", ConfigType.STRING, null), null);

        // Looking up tenantId's config from otherTenant must NOT return it
        // (the service filters by tenantId, so passing otherTenant must yield 404)
        assertThatThrownBy(() -> configService.get(otherTenant, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }
}
