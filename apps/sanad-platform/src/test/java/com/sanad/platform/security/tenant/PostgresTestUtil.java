package com.sanad.platform.security.tenant;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Stage 04A.3 §4 — Utility for asserting PostgreSQL database in tests.
 * Non-skippable: fails immediately if the database is not PostgreSQL.
 */
public final class PostgresTestUtil {

    private PostgresTestUtil() {}

    /**
     * Asserts that the current database is PostgreSQL. Throws
     * AssertionError if not — no graceful skip, no H2 fallback.
     */
    public static void assertPostgreSQL(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equals(productName)) {
                throw new AssertionError(
                    "Mandatory tenant test requires PostgreSQL but found: " + productName +
                    ". This test MUST NOT be skipped or run on H2.");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to verify database product", e);
        }
    }

    /**
     * Returns the current_user from PostgreSQL. Fails if not PostgreSQL.
     */
    public static String getCurrentUser(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT current_user")) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new AssertionError("Failed to get current_user", e);
        }
    }
}
