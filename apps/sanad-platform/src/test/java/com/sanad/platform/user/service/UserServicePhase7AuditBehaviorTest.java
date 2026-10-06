package com.sanad.platform.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.access.service.LastAdminGuard;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.filter.SessionVersionCache;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.exception.DuplicateUserEmailException;
import com.sanad.platform.user.mapper.UserMapper;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 7 behavioral audit contract for tenant user administration mutations:
 * every real, successful mutation emits the exact canonical event through the
 * centralized {@link AccessMutationAuditSupport} adapter, idempotent no-ops
 * emit nothing, failures never emit false SUCCESS, and no payload ever
 * contains credential material.
 */
class UserServicePhase7AuditBehaviorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserRepository userRepository;
    private UserMapper userMapper;
    private AccessMutationAuditSupport audit;
    private UserService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        audit = mock(AccessMutationAuditSupport.class);
        UserService raw = new UserService(
                mock(TenantRepository.class), userRepository, userMapper,
                mock(RefreshTokenRepository.class), mock(SessionVersionCache.class),
                mock(PasswordEncoder.class));
        raw.setAudit(audit);
        raw.setLastAdminGuard(mock(LastAdminGuard.class));
        service = raw;
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createEmitsUserCreatedWithSafeAdministrativeMetadataOnly() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("salem@example.com");
        request.setUsername("salem");
        request.setDisplayName("Salem");

        service.createUser(TENANT_ID, request);

        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).success(eq(TENANT_ID), eq("USER_CREATED"), eq("USER"),
                any(), any(), after.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> afterMap = (Map<String, Object>) after.getValue();
        assertThat(afterMap).containsEntry("status", "INVITED")
                .containsEntry("temporaryAccessProvisioned", false)
                .containsEntry("rotationRequired", false);
        String json = objectMapper.writeValueAsString(afterMap);
        assertThat(json).doesNotContain("initialCredential").doesNotContain("password");
    }

    @Test
    void bootstrapCreateAlsoEmitsDistinctCredentialInitializationFact() throws Exception {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("unit-test-encoded-hash");
        UserService withEncoder = new UserService(
                mock(TenantRepository.class), userRepository, userMapper,
                mock(RefreshTokenRepository.class), mock(SessionVersionCache.class), encoder);
        withEncoder.setAudit(audit);
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("boot@example.com");
        request.setUsername("bootstrapped");
        request.setInitialCredential("S3cret-Temp-Value!");
        request.setStatus(UserStatus.ACTIVE);

        withEncoder.createUser(TENANT_ID, request);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit, org.mockito.Mockito.times(2)).success(eq(TENANT_ID), action.capture(),
                eq("USER"), any(), any(), after.capture());
        assertThat(action.getAllValues()).containsExactly("USER_CREATED", "USER_CREDENTIAL_INITIALIZED");
        String json = objectMapper.writeValueAsString(after.getAllValues());
        // The encoded hash and the raw temporary access secret must never be audited.
        assertThat(json).doesNotContain("S3cret-Temp-Value!")
                .doesNotContain("unit-test-encoded-hash")
                .doesNotContain("initialCredential")
                .doesNotContain("passwordHash");
    }

    @Test
    void realProfileChangeEmitsUserProfileUpdatedWithChangedFields() throws Exception {
        User user = new User(TENANT_ID, "salem@example.com", "Old Name", UserStatus.ACTIVE);
        user.setUsername("salem");
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
        UpdateUserRequest request = new UpdateUserRequest(
                "salem@example.com", "salem", "New Name");

        service.updateUser(TENANT_ID, USER_ID, request);

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).success(eq(TENANT_ID), eq("USER_PROFILE_UPDATED"), eq("USER"),
                eq(USER_ID.toString()), before.capture(), after.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> afterMap = (Map<String, Object>) after.getValue();
        assertThat(afterMap.get("changedFields")).as("changed fields list").isEqualTo(List.of("displayName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeMap = (Map<String, Object>) before.getValue();
        assertThat(beforeMap).containsEntry("displayName", "Old Name");
        assertThat(afterMap).containsEntry("displayName", "New Name");
    }

    @Test
    void usernameChangeEmitsDistinctUserUsernameChangedFact() throws Exception {
        User user = new User(TENANT_ID, "salem@example.com", "Salem", UserStatus.ACTIVE);
        user.setUsername("salem-old");
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
        UpdateUserRequest request = new UpdateUserRequest(
                "salem@example.com", "salem-new", "Salem");

        service.updateUser(TENANT_ID, USER_ID, request);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit, org.mockito.Mockito.times(2)).success(eq(TENANT_ID), action.capture(),
                eq("USER"), eq(USER_ID.toString()), any(), any());
        assertThat(action.getAllValues()).containsExactly("USER_PROFILE_UPDATED", "USER_USERNAME_CHANGED");
    }

    @Test
    void idempotentUpdateEmitsNoFalseChangeAudit() {
        User user = new User(TENANT_ID, "salem@example.com", "Salem", UserStatus.ACTIVE);
        user.setUsername("salem");
        user.setMobileNumber("+966500000000");
        user.setMobileRegion("SA");
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
        UpdateUserRequest request = new UpdateUserRequest(
                "salem@example.com", "salem", "Salem");
        request.setMobileNumber("+966500000000");
        request.setMobileRegion("SA");

        service.updateUser(TENANT_ID, USER_ID, request);

        verifyNoInteractions(audit);
    }

    @Test
    void lifecycleTransitionsEmitExactCanonicalEvents() {
        List<UserStatus> targets = List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED,
                UserStatus.INACTIVE, UserStatus.ARCHIVED);
        List<String> expectedActions = List.of("USER_ACTIVATED", "USER_SUSPENDED",
                "USER_DEACTIVATED", "USER_ARCHIVED");

        for (int i = 0; i < targets.size(); i++) {
            UserStatus from = i == 0 ? UserStatus.INVITED : UserStatus.ACTIVE;
            User user = new User(TENANT_ID, "salem@example.com", "Salem", from);
            when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));

            UserStatus target = targets.get(i);
            switch (target) {
                case ACTIVE -> service.activateUser(TENANT_ID, USER_ID);
                case SUSPENDED -> service.suspendUser(TENANT_ID, USER_ID);
                case INACTIVE -> service.deactivateUser(TENANT_ID, USER_ID);
                case ARCHIVED -> service.archiveUser(TENANT_ID, USER_ID);
                default -> throw new IllegalStateException(target.name());
            }
            verify(audit).success(eq(TENANT_ID), eq(expectedActions.get(i)), eq("USER"),
                    eq(USER_ID.toString()), any(), any());
            org.mockito.Mockito.clearInvocations(audit);
        }
    }

    @Test
    void sameStatusNoOpEmitsNoAudit() {
        User user = new User(TENANT_ID, "salem@example.com", "Salem", UserStatus.ACTIVE);
        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));

        service.activateUser(TENANT_ID, USER_ID);

        verifyNoInteractions(audit);
    }

    @Test
    void failedCreateEmitsNoFalseSuccessAudit() {
        when(userRepository.existsByTenantIdAndEmail(TENANT_ID, "dupe@example.com")).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("dupe@example.com");

        assertThatThrownBy(() -> service.createUser(TENANT_ID, request))
                .isInstanceOf(DuplicateUserEmailException.class);
        verify(audit, never()).success(any(), anyString(), any(), any(), any(), any());
    }
}
