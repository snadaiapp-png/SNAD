package com.sanad.platform.persistence;

import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Canonical PostgreSQL temporal binding policy for all SANAD modules.
 *
 * <p>PostgreSQL JDBC does not infer a SQL type for {@link Instant}. Production
 * JDBC writes must therefore cross the persistence boundary through this
 * adapter (or an equivalent explicit {@link Timestamp} conversion), never by
 * passing raw {@code Instant} values to JdbcTemplate/NamedParameterJdbcTemplate.
 */
public final class PostgresTemporalBindings {

    private PostgresTemporalBindings() {}

    public static void setTimestamptz(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(
                index,
                OffsetDateTime.ofInstant(value, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE);
    }

    public static SqlParameterValue timestamptz(Instant value) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    public static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
