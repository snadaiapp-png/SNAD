package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.CommerceCustomerPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link CommerceCustomerPort} (v20260816.5).
 *
 * <p>Returns the supplied email as the customer reference — i.e. treats
 * every checkout as a guest checkout. Suitable for demo / test deployments
 * where the CRM module is not entitled or where guest checkout is the
 * primary flow.
 *
 * <p>A production deployment should provide a real implementation that
 * resolves or creates a CRM account / contact and returns its UUID (so that
 * orders can be linked to a customer master record).
 */
@Component
public class CommerceCustomerAdapter implements CommerceCustomerPort {

    @Override
    public String resolveOrCreateGuest(UUID tenantId, String email, String name) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required for guest checkout");
        }
        return email.trim().toLowerCase();
    }

    @Override
    public String resolveByContact(UUID tenantId, UUID contactId) {
        // No CRM integration in the default adapter
        return contactId == null ? null : contactId.toString();
    }
}
