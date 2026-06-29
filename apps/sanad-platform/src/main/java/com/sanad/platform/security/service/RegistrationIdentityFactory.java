package com.sanad.platform.security.service;

import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
final class RegistrationIdentityFactory {

    private final TenantRepository tenantRepository;

    RegistrationIdentityFactory(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    String generateSubdomain(String email) {
        String localPart = email.substring(0, email.indexOf('@'))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String base = localPart.length() >= 3 ? localPart : "workspace";
        base = base.substring(0, Math.min(base.length(), 40));

        for (int attempt = 0; attempt < 8; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String candidate = base + "-" + suffix;
            if (!tenantRepository.existsBySubdomain(candidate)) return candidate;
        }
        throw new RegistrationConflictException("Unable to generate a workspace identifier.");
    }

    String normalizeMobileNumber(String countryCode, String localNumber) {
        String digits = localNumber.trim();
        while (digits.startsWith("0")) digits = digits.substring(1);
        String value = countryCode.trim() + digits;
        int digitCount = value.length() - 1;
        boolean valid = value.startsWith("+")
                && digitCount >= 8
                && digitCount <= 15
                && value.substring(1).chars().allMatch(Character::isDigit);
        if (!valid) throw new IllegalArgumentException("Invalid mobile number for selected region.");
        return value;
    }
}
