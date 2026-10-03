package com.sanad.platform.user.service;

import com.sanad.platform.access.service.LastAdminGuard;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.exception.DuplicateUserEmailException;
import com.sanad.platform.user.exception.DuplicateUsernameException;
import com.sanad.platform.user.exception.UserNotFoundException;
import com.sanad.platform.user.mapper.UserMapper;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private LastAdminGuard lastAdminGuard;

    public UserService(TenantRepository tenantRepository, UserRepository userRepository, UserMapper userMapper) {
        this.tenantRepository=tenantRepository;this.userRepository=userRepository;this.userMapper=userMapper;
    }
    @Autowired(required=false) void setLastAdminGuard(LastAdminGuard guard){this.lastAdminGuard=guard;}

    @Transactional
    public UserResponse createUser(UUID tenantId,CreateUserRequest request){
        Objects.requireNonNull(tenantId,"tenantId must not be null");Objects.requireNonNull(request,"CreateUserRequest must not be null");
        String email=normalizeEmail(request.getEmail());
        String username=normalizeUsername(request.getUsername());
        String name=normalizeDisplayName(request.getDisplayName());
        tenantRepository.findById(tenantId).orElseThrow(()->new EntityNotFoundException("Tenant not found with id: "+tenantId));
        if(userRepository.existsByTenantIdAndEmail(tenantId,email))throw new DuplicateUserEmailException(tenantId,email);
        if(username!=null&&userRepository.existsByTenantIdAndUsername(tenantId,username))throw new DuplicateUsernameException(tenantId,username);
        UserStatus initial=request.getStatus()==null?UserStatus.INVITED:request.getStatus();
        User user=new User(tenantId,email,name,initial);
        user.setUsername(username);
        return userMapper.toResponse(userRepository.save(user));
    }
    @Transactional(readOnly=true,propagation=Propagation.SUPPORTS) public List<UserResponse> listUsers(UUID tenantId){Objects.requireNonNull(tenantId,"tenantId must not be null");return userRepository.findByTenantId(tenantId).stream().map(userMapper::toResponse).toList();}
    @Transactional(readOnly=true,propagation=Propagation.SUPPORTS) public UserResponse getUser(UUID tenantId,UUID userId){return userMapper.toResponse(loadUser(tenantId,userId));}

    @Transactional
    public UserResponse updateUser(UUID tenantId,UUID userId,UpdateUserRequest request){
        Objects.requireNonNull(request,"UpdateUserRequest must not be null");User user=loadUser(tenantId,userId);String email=normalizeEmail(request.getEmail());String username=request.getUsername()==null?user.getUsername():normalizeUsername(request.getUsername());
        if(!email.equals(user.getEmail())&&userRepository.existsByTenantIdAndEmail(tenantId,email))throw new DuplicateUserEmailException(tenantId,email);
        if(!Objects.equals(username,user.getUsername())&&username!=null&&userRepository.existsByTenantIdAndUsername(tenantId,username))throw new DuplicateUsernameException(tenantId,username);
        user.setEmail(email);user.setUsername(username);user.setDisplayName(normalizeDisplayName(request.getDisplayName()));return userMapper.toResponse(userRepository.save(user));
    }
    @Transactional public UserResponse activateUser(UUID tenantId,UUID userId){return setStatus(tenantId,userId,UserStatus.ACTIVE);}
    @Transactional public UserResponse deactivateUser(UUID tenantId,UUID userId){return setStatus(tenantId,userId,UserStatus.INACTIVE);}
    @Transactional public UserResponse suspendUser(UUID tenantId,UUID userId){return setStatus(tenantId,userId,UserStatus.SUSPENDED);}
    @Transactional public UserResponse archiveUser(UUID tenantId,UUID userId){return setStatus(tenantId,userId,UserStatus.ARCHIVED);}

    private UserResponse setStatus(UUID tenantId,UUID userId,UserStatus newStatus){
        Objects.requireNonNull(newStatus,"newStatus must not be null");User user=loadUser(tenantId,userId);if(user.getStatus()==newStatus)return userMapper.toResponse(user);
        if(newStatus!=UserStatus.ACTIVE&&lastAdminGuard!=null)lastAdminGuard.assertMayDeactivateUser(tenantId,userId);
        user.setStatus(newStatus);return userMapper.toResponse(userRepository.save(user));
    }
    private User loadUser(UUID tenantId,UUID userId){Objects.requireNonNull(tenantId,"tenantId must not be null");Objects.requireNonNull(userId,"userId must not be null");return userRepository.findByTenantIdAndId(tenantId,userId).orElseThrow(()->new UserNotFoundException(tenantId,userId));}
    private static String normalizeEmail(String email){Objects.requireNonNull(email,"email must not be null");String n=email.trim().toLowerCase(Locale.ROOT);if(n.isBlank())throw new IllegalArgumentException("email must not be blank");if(n.length()>255)throw new IllegalArgumentException("email must be at most 255 characters");return n;}
    private static String normalizeUsername(String username){
        if(username==null)return null;
        String n=username.trim().toLowerCase(Locale.ROOT);
        if(n.isEmpty())return null;
        if(n.length()<3||n.length()>100)throw new IllegalArgumentException("username must be between 3 and 100 characters");
        if(!n.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N}._-]{2,99}$"))throw new IllegalArgumentException("username contains unsupported characters");
        return n;
    }
    private static String normalizeDisplayName(String displayName){if(displayName==null)return null;String n=displayName.trim();if(n.length()>200)throw new IllegalArgumentException("displayName must be at most 200 characters");return n.isEmpty()?null:n;}
}