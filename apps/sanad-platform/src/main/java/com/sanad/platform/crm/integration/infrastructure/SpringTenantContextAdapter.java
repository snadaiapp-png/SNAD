package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.error.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SpringTenantContextAdapter implements TenantContextPort {
    public UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Map<?,?> details) || details.get("tenant_id") == null)
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        return UUID.fromString(details.get("tenant_id").toString());
    }
    public UUID getPrincipalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Map<?,?> details) || details.get("user_id") == null)
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        return UUID.fromString(details.get("user_id").toString());
    }
    public void assertAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
    }
}
