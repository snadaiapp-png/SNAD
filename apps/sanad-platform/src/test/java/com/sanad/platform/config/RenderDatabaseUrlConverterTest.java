package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RenderDatabaseUrlConverter}.
 *
 * <p>Tests both the static {@link RenderDatabaseUrlConverter#parse(String)}
 * method and the {@link RenderDatabaseUrlConverter#postProcessEnvironment}
 * method (Spring context-level integration).</p>
 */
class RenderDatabaseUrlConverterTest {

    // ============================================================
    // Static parse() tests
    // ============================================================

    @Test
    @DisplayName("Valid Render connection string is converted to JDBC format")
    void validConnectionString() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://sanad:secret123@db-host.render.com:5432/sanad");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
        assertThat(result.username).isEqualTo("sanad");
        assertThat(result.password).isEqualTo("secret123");
    }

    @Test
    @DisplayName("URL-encoded username and password are decoded")
    void urlEncodedCredentials() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user%40domain:p%40ss%3Aw0rd@db-host.render.com:5432/sanad");
        assertThat(result.username).isEqualTo("user@domain");
        assertThat(result.password).isEqualTo("p@ss:w0rd");
    }

    @Test
    @DisplayName("%25 in password decodes to literal %")
    void percent25InPassword() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:pass%25word@host:5432/db");
        assertThat(result.password).isEqualTo("pass%word");
    }

    @Test
    @DisplayName("%2B encoded plus decodes to literal +")
    void encodedPlus() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:p%2Bass@host:5432/db");
        assertThat(result.password).isEqualTo("p+ass");
    }

    @Test
    @DisplayName("Literal plus in password is preserved (not converted to space)")
    void literalPlusPreserved() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:p+ass@host:5432/db");
        assertThat(result.password).isEqualTo("p+ass");
    }

    @Test
    @DisplayName("Encoded slash %2F in password decodes to /")
    void encodedSlash() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:pass%2Fword@host:5432/db");
        assertThat(result.password).isEqualTo("pass/word");
    }

    @Test
    @DisplayName("IPv6 hostname is parsed correctly")
    void ipv6Hostname() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:pass@[2001:db8::1]:5432/db");
        assertThat(result.host).isEqualTo("[2001:db8::1]");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://[2001:db8::1]:5432/db");
    }

    @Test
    @DisplayName("Query parameters in URL are ignored for JDBC URL")
    void queryParameters() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:pass@host:5432/db?sslmode=require&foo=bar");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://host:5432/db");
    }

    @Test
    @DisplayName("sslmode query parameter is not included in JDBC URL")
    void sslmodeQueryParam() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://user:pass@host:5432/db?sslmode=require");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://host:5432/db");
        assertThat(result.jdbcUrl).doesNotContain("sslmode");
    }

    @Test
    @DisplayName("Missing credentials throws exception without exposing them")
    void missingCredentials() {
        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(
                "postgresql://db-host.render.com:5432/sanad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials are missing")
                .hasMessageNotContaining("sanad");
    }

    @Test
    @DisplayName("Missing database name throws exception")
    void missingDatabaseName() {
        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(
                "postgresql://sanad:secret@db-host.render.com:5432"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database name is missing");
    }

    @Test
    @DisplayName("Invalid scheme throws exception")
    void invalidScheme() {
        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(
                "mysql://sanad:secret@db-host.render.com:5432/sanad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme must be");
    }

    @Test
    @DisplayName("Missing host throws exception")
    void missingHost() {
        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(
                "postgresql://sanad:secret@/sanad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is missing");
    }

    @Test
    @DisplayName("Default port 5432 is used when port is not specified")
    void defaultPort() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgresql://sanad:secret@db-host.render.com/sanad");
        assertThat(result.port).isEqualTo(5432);
    }

    @Test
    @DisplayName("postgres:// scheme is also accepted")
    void postgresScheme() {
        var result = RenderDatabaseUrlConverter.parse(
                "postgres://sanad:secret@db-host.render.com:5432/sanad");
        assertThat(result.jdbcUrl).isEqualTo("jdbc:postgresql://db-host.render.com:5432/sanad");
    }

    @Test
    @DisplayName("Exception text never contains credential values")
    void exceptionTextNoCredentials() {
        String urlWithCreds = "postgresql://myuser:mypass@host:5432/db";
        // Force a failure by removing the database name
        String badUrl = "postgresql://myuser:mypass@host:5432";
        assertThatThrownBy(() -> RenderDatabaseUrlConverter.parse(badUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("myuser")
                .hasMessageNotContaining("mypass");
    }

    // ============================================================
    // postProcessEnvironment tests
    // ============================================================

    @Test
    @DisplayName("postProcessEnvironment: prod profile converts RENDER_DATABASE_URL")
    void postProcess_prodProfile_converts() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        env.setProperty("RENDER_DATABASE_URL",
                "postgresql://testuser:testpass@dbhost:5432/testdb");

        converter.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dbhost:5432/testdb");
        assertThat(env.getProperty("spring.datasource.username"))
                .isEqualTo("testuser");
        assertThat(env.getProperty("spring.datasource.password"))
                .isEqualTo("testpass");
        assertThat(env.getProperty("sanad.database.url"))
                .isEqualTo("jdbc:postgresql://dbhost:5432/testdb");
    }

    @Test
    @DisplayName("postProcessEnvironment: non-prod profile does not convert")
    void postProcess_nonProdProfile_doesNotConvert() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "local");
        env.setProperty("RENDER_DATABASE_URL",
                "postgresql://testuser:testpass@dbhost:5432/testdb");

        converter.postProcessEnvironment(env, new SpringApplication());

        // Properties should not be set
        assertThat(env.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    @DisplayName("postProcessEnvironment: explicit DATABASE_URL overrides converter")
    void postProcess_explicitDatabaseUrlOverrides() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        env.setProperty("RENDER_DATABASE_URL",
                "postgresql://convuser:convpass@convhost:5432/convdb");
        env.setProperty("DATABASE_URL", "jdbc:postgresql://explicit:5432/explicitdb");

        converter.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://explicit:5432/explicitdb");
    }

    @Test
    @DisplayName("postProcessEnvironment: explicit DATABASE_USERNAME overrides converter")
    void postProcess_explicitUsernameOverrides() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        env.setProperty("RENDER_DATABASE_URL",
                "postgresql://convuser:convpass@convhost:5432/convdb");
        env.setProperty("DATABASE_USERNAME", "explicituser");

        converter.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.username"))
                .isEqualTo("explicituser");
        // Password should still come from converter
        assertThat(env.getProperty("spring.datasource.password"))
                .isEqualTo("convpass");
    }

    @Test
    @DisplayName("postProcessEnvironment: explicit DATABASE_PASSWORD overrides converter")
    void postProcess_explicitPasswordOverrides() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        env.setProperty("RENDER_DATABASE_URL",
                "postgresql://convuser:convpass@convhost:5432/convdb");
        env.setProperty("DATABASE_PASSWORD", "explicitpass");

        converter.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.password"))
                .isEqualTo("explicitpass");
        // Username should still come from converter
        assertThat(env.getProperty("spring.datasource.username"))
                .isEqualTo("convuser");
    }

    @Test
    @DisplayName("postProcessEnvironment: no RENDER_DATABASE_URL does nothing")
    void postProcess_noRenderUrl_doesNothing() {
        var converter = new RenderDatabaseUrlConverter();
        var env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");

        converter.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url")).isNull();
    }
}
