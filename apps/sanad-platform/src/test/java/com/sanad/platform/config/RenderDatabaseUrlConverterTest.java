package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RenderDatabaseUrlConverter}.
 *
 * <p>Verifies that Render's PostgreSQL connection string format
 * is correctly parsed and converted to JDBC format. Also verifies
 * that invalid inputs are rejected and credentials are never
 * included in exception messages.</p>
 */
class RenderDatabaseUrlConverterTest {

    @Test
    @DisplayName("Valid Render connection string is converted to JDBC format")
    void validConnectionString() {
        String renderUrl = "postgresql://sanad:secret123@db-host.render.com:5432/sanad";

        RenderDatabaseUrlConverter.ParsedUrl result =
                RenderDatabaseUrlConverter.parse(renderUrl);

        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
        assertThat(result.username).isEqualTo("sanad");
        assertThat(result.password).isEqualTo("secret123");
        assertThat(result.host).isEqualTo("db-host.render.com");
        assertThat(result.port).isEqualTo(5432);
        assertThat(result.database).isEqualTo("sanad");
    }

    @Test
    @DisplayName("URL-encoded username and password are decoded")
    void urlEncodedCredentials() {
        // %40 = @, %3A = :, %2F = /
        String renderUrl = "postgresql://user%40domain:p%40ss%3Aw0rd@db-host.render.com:5432/sanad";

        RenderDatabaseUrlConverter.ParsedUrl result =
                RenderDatabaseUrlConverter.parse(renderUrl);

        assertThat(result.username).isEqualTo("user@domain");
        assertThat(result.password).isEqualTo("p@ss:w0rd");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
    }

    @Test
    @DisplayName("Missing credentials throws exception without exposing them")
    void missingCredentials() {
        String renderUrl = "postgresql://db-host.render.com:5432/sanad";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials are missing")
                .hasMessageNotContaining("sanad");
    }

    @Test
    @DisplayName("Missing database name throws exception")
    void missingDatabaseName() {
        String renderUrl = "postgresql://sanad:secret@db-host.render.com:5432";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database name is missing");
    }

    @Test
    @DisplayName("Invalid scheme throws exception")
    void invalidScheme() {
        String renderUrl = "mysql://sanad:secret@db-host.render.com:5432/sanad";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme must be");
    }

    @Test
    @DisplayName("Missing password in credentials throws exception without exposing username")
    void missingPassword() {
        String renderUrl = "postgresql://sanad@db-host.render.com:5432/sanad";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password is missing")
                .hasMessageNotContaining("sanad");
    }

    @Test
    @DisplayName("Missing host throws exception")
    void missingHost() {
        String renderUrl = "postgresql://sanad:secret@/sanad";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is missing");
    }

    @Test
    @DisplayName("Default port 5432 is used when port is not specified")
    void defaultPort() {
        String renderUrl = "postgresql://sanad:secret@db-host.render.com/sanad";

        RenderDatabaseUrlConverter.ParsedUrl result =
                RenderDatabaseUrlConverter.parse(renderUrl);

        assertThat(result.port).isEqualTo(5432);
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
    }

    @Test
    @DisplayName("Malformed URI throws exception without exposing details")
    void malformedUri() {
        String renderUrl = "not a url at all";

        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(renderUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed URI");
    }

    @Test
    @DisplayName("postgres:// scheme is also accepted")
    void postgresScheme() {
        String renderUrl = "postgres://sanad:secret@db-host.render.com:5432/sanad";

        RenderDatabaseUrlConverter.ParsedUrl result =
                RenderDatabaseUrlConverter.parse(renderUrl);

        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
    }
}
