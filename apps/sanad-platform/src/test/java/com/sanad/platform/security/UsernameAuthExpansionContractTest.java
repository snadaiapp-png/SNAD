package com.sanad.platform.security;

import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * RED-first contract for the User Administration expansion.
 *
 * <p>This test intentionally lands before the username implementation. It pins
 * the additive public/domain contract so the subsequent GREEN implementation
 * cannot silently replace email login, weaken tenant scoping, or invent a
 * parallel identity store.</p>
 */
class UsernameAuthExpansionContractTest {

    @Test
    void userDomainMustExposeTenantScopedUsernameProperty() {
        assertDoesNotThrow(() -> User.class.getDeclaredField("username"));
        assertDoesNotThrow(() -> User.class.getDeclaredMethod("getUsername"));
        assertDoesNotThrow(() -> User.class.getDeclaredMethod("setUsername", String.class));
    }

    @Test
    void userTransportContractsMustExposeUsernameAdditively() {
        assertUsernameAccessors(CreateUserRequest.class);
        assertUsernameAccessors(UpdateUserRequest.class);
        assertUsernameAccessors(UserResponse.class);
    }

    @Test
    void repositoryMustProvideTenantScopedAndCrossTenantUsernameLookups() {
        assertMethod(UserRepository.class, "findByTenantIdAndUsername", UUID.class, String.class);
        assertMethod(UserRepository.class, "existsByTenantIdAndUsername", UUID.class, String.class);
        assertMethod(UserRepository.class, "findAllByUsername", String.class);
    }

    @Test
    void loginRequestMustSupportUsernameWithoutBreakingLegacyEmailLogin() throws Exception {
        assertUsernameAccessors(LoginRequest.class);

        Field email = LoginRequest.class.getDeclaredField("email");

        // Username-only login cannot pass Bean Validation while email remains
        // unconditionally @NotBlank. Exactly-one-identifier validation belongs
        // at request/class validation or service validation instead.
        assertThat(email.getAnnotation(NotBlank.class))
                .as("email must become optional when username is supplied")
                .isNull();

        // Legacy email accessors remain part of the compatibility contract.
        assertMethod(LoginRequest.class, "getEmail");
        assertMethod(LoginRequest.class, "setEmail", String.class);
    }

    @Test
    void aForwardOnlyPostgresqlMigrationMustIntroduceUsernameWithoutFabricatingBackfill() throws Exception {
        Path migrationDir = Path.of("src/main/resources/db/migration");
        assertThat(Files.isDirectory(migrationDir)).isTrue();

        String matchingMigration;
        try (var stream = Files.list(migrationDir)) {
            matchingMigration = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> {
                        try {
                            return path.getFileName() + "\n" + Files.readString(path);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .filter(text -> text.toLowerCase().contains("username")
                            && text.toLowerCase().contains("users"))
                    .filter(text -> text.toLowerCase().contains("add column")
                            || text.toLowerCase().contains("add column if not exists"))
                    .findFirst()
                    .orElse(null);
        }

        assertThat(matchingMigration)
                .as("a forward-only users.username migration must exist")
                .isNotNull();

        String normalized = matchingMigration.toLowerCase();
        assertThat(normalized).contains("tenant_id");
        assertThat(normalized).contains("unique");
        assertThat(normalized)
                .as("migration must not fabricate usernames for legacy users")
                .doesNotContain("set username =");
    }

    @Test
    void authServiceMustResolveUsernameThroughCanonicalUserRepository() throws Exception {
        Path source = Path.of("src/main/java/com/sanad/platform/security/service/AuthService.java");
        String text = Files.readString(source);

        assertThat(text).contains("findByTenantIdAndUsername");
        assertThat(text).contains("findAllByUsername");

        // Existing email path is deliberately retained.
        assertThat(text).contains("findByTenantIdAndEmail");
        assertThat(text).contains("findAllByEmail");
    }

    private static void assertUsernameAccessors(Class<?> type) {
        assertMethod(type, "getUsername");
        assertMethod(type, "setUsername", String.class);
    }

    private static Method assertMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            assertThat(method).isNotNull();
            return method;
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    type.getSimpleName() + " must declare " + name, exception);
        }
    }
}
