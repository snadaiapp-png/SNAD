package com.sanad.platform.shared.jdbc;

import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Canonical PostgreSQL temporal binding for all SANAD modules.
 *
 * <p>PostgreSQL JDBC does not infer a SQL type for {@link Instant}. Every
 * TIMESTAMPTZ write must cross this boundary (or an equivalent explicit
 * {@code Timestamp.from(...)} conversion for legacy TIMESTAMP columns).
 * This class centralizes the preferred TIMESTAMPTZ behavior so current and
 * future modules cannot depend on driver-specific inference.</p>
 */
public final class PostgresTemporalBinding {
    private PostgresTemporalBinding() {}

    public static OffsetDateTime timestamptzValue(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static SqlParameterValue timestamptzParameter(Instant instant) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, timestamptzValue(instant));
    }

    public static void setTimestamptz(PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        if (instant == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(index, timestamptzValue(instant), Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
