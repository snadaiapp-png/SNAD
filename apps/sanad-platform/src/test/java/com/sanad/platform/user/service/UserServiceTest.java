package com.sanad.platform.user.service;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.exception.DuplicateUserEmailException;
import com.sanad.platform.user.exception.UserNotFoundException;
import com.sanad.platform.user.mapper.UserMapper;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for UserService tenant isolation and lifecycle behavior. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService service;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID userId;
    private Tenant tenant;
    private User user;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        otherTenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        userId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        tenant = new Tenant("Acme", "acme", TenantStatus.ACTIVE);
        reflectSet(tenant, "id", tenantId);

        user = new User(tenantId, "alice@example.com", "Alice", UserStatus.INVITED);
        reflectSet(user, "id", userId);
        reflectSet(user, "createdAt", Instant.parse("2026-06-18T00:00:00Z"));
        reflectSet(user, "updatedAt", Instant.parse("2026-06-18T00:00:00Z"));
    }

    @Test
    @DisplayName("createUser: creates an INVITED user by default")
    void createUser_success_defaultsToInvited() {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        stubSuccessfulCreate(tenantId, request.getEmail());

        UserResponse result = service.createUser(tenantId, request);

        assertThat(result.getStatus()).isEqualTo(UserStatus.INVITED);
        verify(userRepository).existsByTenantIdAndEmail(tenantId, "alice@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser: normalizes email before duplicate check and save")
    void createUser_normalizesEmail() {
        CreateUserRequest request = new CreateUserRequest("  Alice@Example.COM  ", " Alice ", null);
        stubSuccessfulCreate(tenantId, "alice@example.com");

        service.createUser(tenantId, request);

        verify(userRepository).existsByTenantIdAndEmail(tenantId, "alice@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("createUser: rejects duplicate email in the same tenant")
    void createUser_duplicateInSameTenant_rejected() {
        CreateUserRequest request = new CreateUserRequest("Alice@Example.com", "Alice", null);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByTenantIdAndEmail(tenantId, "alice@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createUser(tenantId, request))
                .isInstanceOf(DuplicateUserEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser: permits the same normalized email in another tenant")
    void createUser_sameEmailDifferentTenant_allowed() {
        Tenant otherTenant = new Tenant("Other", "other", TenantStatus.ACTIVE);
        reflectSet(otherTenant, "id", otherTenantId);
        CreateUserRequest request = new CreateUserRequest("Alice@Example.com", "Alice", null);

        when(tenantRepository.findById(otherTenantId)).thenReturn(Optional.of(otherTenant));
        when(userRepository.existsByTenantIdAndEmail(otherTenantId, "alice@example.com"))
                .thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation ->
                toResponse(invocation.getArgument(0)));

        UserResponse result = service.createUser(otherTenantId, request);

        assertThat(result.getTenantId()).isEqualTo(otherTenantId);
        verify(userRepository).existsByTenantIdAndEmail(otherTenantId, "alice@example.com");
    }

    @Test
    @DisplayName("createUser: rejects an unknown tenant")
    void createUser_unknownTenant_rejected() {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(tenantId, request))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("createUser: honors an explicit valid status")
    void createUser_explicitStatus_honored() {
        CreateUserRequest request =
                new CreateUserRequest("alice@example.com", "Alice", UserStatus.ACTIVE);
        stubSuccessfulCreate(tenantId, request.getEmail());

        UserResponse result = service.createUser(tenantId, request);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("listUsers: returns users only from the requested tenant")
    void listUsers_tenantScoped() {
        User second = new User(tenantId, "bob@example.com", "Bob", UserStatus.ACTIVE);
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(user, second));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation ->
                toResponse(invocation.getArgument(0)));

        List<UserResponse> result = service.listUsers(tenantId);

        assertThat(result).hasSize(2);
        verify(userRepository).findByTenantId(tenantId);
    }

    @Test
    @DisplayName("getUser: returns a tenant-scoped user")
    void getUser_success() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(toResponse(user));

        UserResponse result = service.getUser(tenantId, userId);

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("getUser: cross-tenant lookup is indistinguishable from not found")
    void getUser_crossTenant_rejected() {
        when(userRepository.findByTenantIdAndId(otherTenantId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(otherTenantId, userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");

        verify(userMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("updateUser: updates the display name")
    void updateUser_updatesDisplayName() {
        UpdateUserRequest request = new UpdateUserRequest("alice@example.com", "Alice Updated");
        stubExistingUserForWrite();
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(user));

        UserResponse result = service.updateUser(tenantId, userId, request);

        assertThat(result.getDisplayName()).isEqualTo("Alice Updated");
    }

    @Test
    @DisplayName("updateUser: normalizes changed email before duplicate check and save")
    void updateUser_normalizesEmail() {
        UpdateUserRequest request = new UpdateUserRequest("  NEW@Example.COM ", "Alice");
        stubExistingUserForWrite();
        when(userRepository.existsByTenantIdAndEmail(tenantId, "new@example.com"))
                .thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(user));

        UserResponse result = service.updateUser(tenantId, userId, request);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).existsByTenantIdAndEmail(tenantId, "new@example.com");
    }

    @Test
    @DisplayName("updateUser: rejects an email already used in the same tenant")
    void updateUser_duplicateEmail_rejected() {
        UpdateUserRequest request = new UpdateUserRequest("bob@example.com", "Alice");
        stubExistingUserForWrite();
        when(userRepository.existsByTenantIdAndEmail(tenantId, "bob@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateUser(tenantId, userId, request))
                .isInstanceOf(DuplicateUserEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("activateUser: sets ACTIVE")
    void activateUser_setsActive() {
        assertStatusTransition(UserStatus.ACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("deactivateUser: sets INACTIVE")
    void deactivateUser_setsInactive() {
        assertStatusTransition(UserStatus.INACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("suspendUser: sets SUSPENDED")
    void suspendUser_setsSuspended() {
        assertStatusTransition(UserStatus.SUSPENDED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("archiveUser: sets ARCHIVED")
    void archiveUser_setsArchived() {
        assertStatusTransition(UserStatus.ARCHIVED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ARCHIVED);
    }

    @Test
    @DisplayName("lifecycle operation: unknown tenant/user tuple raises UserNotFoundException")
    void lifecycle_notFound_rejected() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendUser(tenantId, userId))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("service never uses inherited unscoped UserRepository operations")
    void noUnscopedRepositoryOperationsUsed() {
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());

        service.listUsers(tenantId);

        verify(userRepository).findByTenantId(tenantId);
        verify(userRepository, never()).findAll();
        verify(userRepository, never()).findById(any(UUID.class));
        verify(userRepository, never()).deleteById(any(UUID.class));
        verify(userRepository, never()).deleteAll();
    }

    private void stubSuccessfulCreate(UUID scopeTenantId, String email) {
        when(tenantRepository.findById(scopeTenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByTenantIdAndEmail(
                scopeTenantId, email.trim().toLowerCase())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation ->
                toResponse(invocation.getArgument(0)));
    }

    private void stubExistingUserForWrite() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
    }

    private void assertStatusTransition(UserStatus expectedStatus) {
        stubExistingUserForWrite();
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(user));

        UserResponse result = switch (expectedStatus) {
            case ACTIVE -> service.activateUser(tenantId, userId);
            case INACTIVE -> service.deactivateUser(tenantId, userId);
            case SUSPENDED -> service.suspendUser(tenantId, userId);
            case ARCHIVED -> service.archiveUser(tenantId, userId);
            case INVITED -> throw new IllegalArgumentException("Unsupported transition");
        };

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        verify(userRepository).save(user);
    }

    private static UserResponse toResponse(User source) {
        return new UserResponse(
                source.getId(),
                source.getTenantId(),
                source.getEmail(),
                source.getDisplayName(),
                source.getStatus(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
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
