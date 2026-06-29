package com.sanad.platform.security.service;

import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationIdentityFactoryTest {

    @Mock TenantRepository tenantRepository;

    @Test
    void normalizesSaudiLocalMobileToE164() {
        RegistrationIdentityFactory factory = new RegistrationIdentityFactory(tenantRepository);
        String localNumber = "0" + "5" + "0".repeat(8);
        assertEquals("+966" + localNumber.substring(1),
                factory.normalizeMobileNumber("+966", localNumber));
    }

    @Test
    void generatesAvailableWorkspaceIdentifierFromEmail() {
        when(tenantRepository.existsBySubdomain(anyString())).thenReturn(false);
        RegistrationIdentityFactory factory = new RegistrationIdentityFactory(tenantRepository);
        String subdomain = factory.generateSubdomain("owner@example.com");
        assertTrue(subdomain.startsWith("owner-"));
        assertTrue(subdomain.length() <= 49);
    }
}
