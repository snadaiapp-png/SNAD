package com.sanad.platform.crm.query.infrastructure;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTimelineProjectionRepositoryTest {

    private static final Instant EXPECTED = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void convertsSupportedJdbcTimestampRepresentations() {
        assertThat(JdbcTimelineProjectionRepository.toInstant(EXPECTED)).isEqualTo(EXPECTED);
        assertThat(JdbcTimelineProjectionRepository.toInstant(Timestamp.from(EXPECTED))).isEqualTo(EXPECTED);
        assertThat(JdbcTimelineProjectionRepository.toInstant(
                OffsetDateTime.ofInstant(EXPECTED, ZoneOffset.ofHours(3)))).isEqualTo(EXPECTED);
        assertThat(JdbcTimelineProjectionRepository.toInstant(
                ZonedDateTime.ofInstant(EXPECTED, ZoneOffset.ofHours(3)))).isEqualTo(EXPECTED);
        assertThat(JdbcTimelineProjectionRepository.toInstant(
                LocalDateTime.ofInstant(EXPECTED, ZoneOffset.UTC))).isEqualTo(EXPECTED);
    }

    @Test
    void rejectsNullAndUnknownJdbcTimestampRepresentations() {
        assertThatThrownBy(() -> JdbcTimelineProjectionRepository.toInstant(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be null");

        assertThatThrownBy(() -> JdbcTimelineProjectionRepository.toInstant("2026-07-17T12:00:00Z"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported occurred_at JDBC type");
    }
}
