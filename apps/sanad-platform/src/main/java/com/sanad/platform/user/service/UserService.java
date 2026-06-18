package com.sanad.platform.user.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped application service for the User aggregate.
 *
 * <p>All reads and writes use explicit tenant-scoped repository methods.
 * Inherited unscoped JpaRepository operations are intentionally never used.</p>
 */
@Service
public class UserService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       UserMapper userMapper) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse createUser(UUID tenantId, CreateUserRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "CreateUserRequest must not be null");

        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedDisplayName = normalizeDisplayName(request.getDisplayName());

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        if (userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateUserEmailException(tenantId, normalizedEmail);
        }

        UserStatus initialStatus = request.getStatus() == null
                ? UserStatus.INVITED
                : request.getStatus();

        User user = new User(
                tenantId,
                normalizedEmail,
                normalizedDisplayName,
                initialStatus
        );

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<UserResponse> listUsers(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return userRepository.findByTenantId(tenantId).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public UserResponse getUser(UUID tenantId, UUID userId) {
        return userMapper.toResponse(loadUser(tenantId, userId));
    }

    @Transactional
    public UserResponse updateUser(UUID tenantId, UUID userId, UpdateUserRequest request) {
        Objects.requireNonNull(request, "UpdateUserRequest must not be null");

        User user = loadUser(tenantId, userId);
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (!normalizedEmail.equals(user.getEmail())
                && userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new DuplicateUserEmailException(tenantId, normalizedEmail);
        }

        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizeDisplayName(request.getDisplayName()));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse activateUser(UUID tenantId, UUID userId) {
        return setStatus(tenantId, userId, UserStatus.ACTIVE);
    }

    @Transactional
    public UserResponse deactivateUser(UUID tenantId, UUID userId) {
        return setStatus(tenantId, userId, UserStatus.INACTIVE);
    }

    @Transactional
    public UserResponse suspendUser(UUID tenantId, UUID userId) {
        return setStatus(tenantId, userId, UserStatus.SUSPENDED);
    }

    @Transactional
    public UserResponse archiveUser(UUID tenantId, UUID userId) {
        return setStatus(tenantId, userId, UserStatus.ARCHIVED);
    }

    private UserResponse setStatus(UUID tenantId, UUID userId, UserStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        User user = loadUser(tenantId, userId);
        if (user.getStatus() == newStatus) {
            return userMapper.toResponse(user);
        }

        user.setStatus(newStatus);
        return userMapper.toResponse(userRepository.save(user));
    }

    private User loadUser(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        return userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new UserNotFoundException(tenantId, userId));
    }

    private static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("email must be at most 255 characters");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        String normalized = displayName.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("displayName must be at most 200 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
