package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.domain.PasswordResetToken;
import com.sanad.platform.security.domain.PasswordResetTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.dto.ForgotPasswordRequest;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.security.ratelimit.LoginRateLimiter;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordRecoverySecurityTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private SessionVersionCache sessionVersionCache;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                new SecurityProperties(),
                loginRateLimiter,
                sessionVersionCache);
    }

    @Test
    void ambiguousActiveEmailAcrossTenantsFailsClosed() {
        User first = user(
                "11111111-1111-4111-8111-111111111111",
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "shared@example.com",
                UserStatus.ACTIVE);
        User second = user(
                "22222222-2222-4222-8222-222222222222",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                "shared@example.com",
                UserStatus.ACTIVE);
        when(userRepository.findAllByEmail("shared@example.com"))
                .thenReturn(List.of(first, second));

        String token = service.initiatePasswordReset(
                new ForgotPasswordRequest("shared@example.com"),
                "127.0.0.1");

        assertThat(token).isNull();
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(passwordResetTokenRepository, never()).revokeAllActive(any(), any());
    }

    @Test
    void inactiveOnlyEmailDoesNotCreateCredentialMutationToken() {
        User suspended = user(
                "11111111-1111-4111-8111-111111111111",
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "inactive@example.com",
                UserStatus.SUSPENDED);
        when(userRepository.findAllByEmail("inactive@example.com"))
                .thenReturn(List.of(suspended));

        String token = service.initiatePasswordReset(
                new ForgotPasswordRequest("inactive@example.com"),
                "127.0.0.1");

        assertThat(token).isNull();
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void uniqueActiveEmailCreatesTenantBoundResetToken() {
        UUID tenantId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        User active = user(
                tenantId.toString(),
                userId.toString(),
                "unique@example.com",
                UserStatus.ACTIVE);
        when(userRepository.findAllByEmail("unique@example.com"))
                .thenReturn(List.of(active));

        String token = service.initiatePasswordReset(
                new ForgotPasswordRequest("unique@example.com"),
                "127.0.0.1");

        assertThat(token).isNotBlank();
        verify(passwordResetTokenRepository).revokeAllActive(tenantId, userId);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void authServiceDoesNotExposeDirectAdministrativePasswordOverwrite() {
        assertThat(AuthService.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("adminResetPassword"));
    }

    private static User user(String tenantId, String userId, String email, UserStatus status) {
        User user = new User(UUID.fromString(tenantId), email, email, status);
        reflectSet(user, "id", UUID.fromString(userId));
        return user;
    }

    private static void reflectSet(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to set test field " + fieldName, exception);
        }
    }
}
