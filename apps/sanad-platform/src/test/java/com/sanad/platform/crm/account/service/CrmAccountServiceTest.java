package com.sanad.platform.crm.account.service;

import com.sanad.platform.crm.account.api.CreateCrmAccountRequest;
import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import com.sanad.platform.crm.account.domain.CrmAccountType;
import com.sanad.platform.crm.account.repository.CrmAccountQueryRepository;
import com.sanad.platform.crm.account.repository.CrmAccountRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmAccountServiceTest {
    @Mock private TenantRepository tenants;
    @Mock private UserRepository users;
    @Mock private CrmAccountRepository accounts;
    @Mock private CrmAccountQueryRepository accountQueries;

    private CrmAccountService service;
    private UUID tenantId;
    private UUID actorId;
    private Tenant activeTenant;

    @BeforeEach
    void setUp() {
        service = new CrmAccountService(tenants, users, accounts, accountQueries);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        activeTenant = new Tenant("Global Tenant", "global-tenant", TenantStatus.ACTIVE);
    }

    @Test
    void rejectsOwnerOutsideAuthenticatedTenant() {
        UUID foreignOwner = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(users.findByTenantIdAndId(tenantId, foreignOwner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                tenantId,
                actorId,
                request(foreignOwner)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("owner");

        verify(accounts, never()).save(any(CrmAccount.class));
    }

    @Test
    void acceptsOwnerInsideAuthenticatedTenant() {
        User owner = org.mockito.Mockito.mock(User.class);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(users.findByTenantIdAndId(tenantId, actorId)).thenReturn(Optional.of(owner));
        when(accounts.save(any(CrmAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(tenantId, actorId, request(actorId));

        verify(accounts).save(any(CrmAccount.class));
    }

    @Test
    void rejectsInactiveTenant() {
        Tenant inactive = new Tenant("Inactive", "inactive", TenantStatus.INACTIVE);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(tenantId, actorId, request(null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Active tenant");

        verify(accounts, never()).save(any(CrmAccount.class));
    }

    @Test
    void boundsAccountListToOneHundredRecords() {
        when(accountQueries.findByTenant_IdAndLifecycleStatusNot(
                any(UUID.class), any(CrmAccountStatus.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(tenantId);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(accountQueries).findByTenant_IdAndLifecycleStatusNot(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(CrmAccountStatus.ARCHIVED),
                pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    private CreateCrmAccountRequest request(UUID ownerId) {
        return new CreateCrmAccountRequest(
                "Acme",
                CrmAccountType.BUSINESS,
                ownerId,
                "USD",
                "en-US",
                "UTC",
                "TEST");
    }
}
