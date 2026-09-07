package com.sanad.platform.hr.integration;

import com.sanad.platform.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Adapter from the HR {@link IamEmploymentAccessPort} to the existing
 * {@link UserService} (WS4 Task 7).
 *
 * <p>This adapter is the SINGLE point where the HR module touches the user
 * service. HR production code must never query or mutate user-account tables
 * directly — only through {@link IamEmploymentAccessPort}, whose only
 * production implementation is this adapter. Enforced by the WS4 Task 9
 * module-boundary architecture test.</p>
 */
@Component
public class UserServiceIamEmploymentAccessAdapter implements IamEmploymentAccessPort {

    private final UserService userService;

    @Autowired
    public UserServiceIamEmploymentAccessAdapter(UserService userService) {
        this.userService = Objects.requireNonNull(userService, "userService");
    }

    @Override
    public void disableUserAccount(UUID tenantId, UUID userId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        userService.suspendUser(tenantId, userId);
    }

    @Override
    public void enableUserAccount(UUID tenantId, UUID userId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        userService.activateUser(tenantId, userId);
    }

    @Override
    public boolean isUserAccountActive(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        var response = userService.getUser(tenantId, userId);
        return response != null && "ACTIVE".equals(String.valueOf(response.getStatus()));
    }
}
