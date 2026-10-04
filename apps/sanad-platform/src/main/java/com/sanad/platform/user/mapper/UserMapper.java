package com.sanad.platform.user.mapper;

import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Maps User domain entities to transport-safe response DTOs. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getMobileNumber(),
                user.getMobileRegion(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}