package com.sanad.platform.crm.mobile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ISO-006 contract guard.
 *
 * The production rule is enforced by PostgreSQL, not by a single HTTP code path,
 * so every future device-registration mechanism is subject to the same limit.
 * Flyway integration validates SQL execution; this focused test prevents the
 * concurrency lock, threshold, or trigger from being accidentally removed.
 */
class G7DeviceLimitMigrationTest {

    @Test
    void migrationEnforcesFiveActiveDevicesWithConcurrentRegistrationSerialization() throws IOException {
        String resource = "/db/migration/V20260820_3__enforce_mobile_device_limit.sql";
        try (InputStream stream = G7DeviceLimitMigrationTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "ISO-006 migration must be packaged with the application");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("active_device_count >= 5"),
                    "policy must reject the sixth ACTIVE device");
            assertTrue(sql.contains("pg_advisory_xact_lock"),
                    "concurrent registrations for one tenant/user must serialize");
            assertTrue(sql.contains("BEFORE INSERT OR UPDATE OF status, tenant_id, user_id"),
                    "all activation/ownership entry paths must be guarded");
            assertTrue(sql.contains("MOBILE_DEVICE_LIMIT_EXCEEDED"),
                    "limit violation must expose a stable machine-readable error");
        }
    }
}
