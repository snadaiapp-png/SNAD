package com.sanad.platform.access.grant;

import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.authorization.AuthorizationMutationCoordinator;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserRoleGrantService {
    private final UserRoleGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleService roleService;
    private AccessMutationAuditSupport audit;
    private AuthorizationMutationCoordinator authChanges;

    public UserRoleGrantService(UserRoleGrantRepository grantRepository, UserRepository userRepository,
            OrganizationRepository organizationRepository, RoleService roleService) {
        this.grantRepository=grantRepository; this.userRepository=userRepository;
        this.organizationRepository=organizationRepository; this.roleService=roleService;
    }
    @Autowired(required=false) void setAudit(AccessMutationAuditSupport audit){this.audit=audit;}
    @Autowired(required=false) void setAuthChanges(AuthorizationMutationCoordinator authChanges){this.authChanges=authChanges;}

    @Transactional
    public UserAccessResponse grant(UUID tenantId, UUID userId, UUID roleId, UUID organizationId) {
        requireUser(tenantId,userId); Role role=roleService.load(tenantId,roleId);
        if(role.getStatus()!=RoleStatus.ACTIVE) throw new IllegalStateException("Only active roles can be granted");
        requireOrganization(tenantId,organizationId);
        Optional<UserRoleGrant> existing=organizationId==null
                ?grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(tenantId,userId,roleId)
                :grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationId(tenantId,userId,roleId,organizationId);
        UserRoleGrant grant=existing.orElseGet(()->new UserRoleGrant(tenantId,userId,roleId,organizationId));
        boolean authorityChanged = existing.isEmpty() || grant.getStatus() == UserGrantStatus.REVOKED;
        if(grant.getStatus()==UserGrantStatus.REVOKED) grant.setStatus(UserGrantStatus.ACTIVE);
        if(grant.getId()==null||existing.isPresent()) grant=grantRepository.save(grant);
        audit(tenantId,"USER_ROLE_GRANT",grant.getId(),null,
                Map.of("userId",userId,"roleId",roleId,"scope",organizationId==null?"TENANT":"ORGANIZATION"));
        if (authorityChanged && authChanges != null) {
            authChanges.subjectChanged(tenantId, userId, "USER_ROLE_CHANGED", "USER_ROLE_GRANT", grant.getId());
        }
        return response(grant,role.getCode());
    }

    @Transactional
    public UserAccessResponse revoke(UUID tenantId, UUID grantId) {
        UserRoleGrant grant=grantRepository.findByTenantIdAndId(tenantId,grantId)
                .orElseThrow(()->new AccessResourceNotFoundException("User role grant not found"));
        UserGrantStatus before=grant.getStatus();
        grant.setStatus(UserGrantStatus.REVOKED); grant=grantRepository.save(grant);
        Role role=roleService.load(tenantId,grant.getRoleId());
        audit(tenantId,"USER_ROLE_REVOKE",grantId,
                Map.of("status",before.name(),"userId",grant.getUserId()),
                Map.of("status",grant.getStatus().name(),"userId",grant.getUserId()));
        if (before != UserGrantStatus.REVOKED && authChanges != null) {
            authChanges.subjectChanged(tenantId, grant.getUserId(), "USER_ROLE_CHANGED", "USER_ROLE_GRANT", grantId);
        }
        return response(grant,role.getCode());
    }

    @Transactional(readOnly=true)
    public List<UserAccessResponse> list(UUID tenantId,UUID userId){
        requireUser(tenantId,userId);
        return grantRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId,userId).stream()
                .map(g->response(g,roleService.load(tenantId,g.getRoleId()).getCode())).toList();
    }
    public List<UserRoleGrant> activeGrants(UUID tenantId,UUID userId){
        requireUser(tenantId,userId);
        return grantRepository.findByTenantIdAndUserIdAndStatus(tenantId,userId,UserGrantStatus.ACTIVE);
    }
    private void audit(UUID tenantId,String action,UUID id,Object before,Object after){
        if(audit!=null)audit.success(tenantId,action,"USER_ROLE_GRANT",id==null?null:id.toString(),before,after);
    }
    private void requireUser(UUID tenantId,UUID userId){
        if(tenantId==null||userId==null||userRepository.findByTenantIdAndId(tenantId,userId).isEmpty())
            throw new AccessResourceNotFoundException("User not found");
    }
    private void requireOrganization(UUID tenantId,UUID organizationId){
        if(organizationId!=null&&organizationRepository.findByTenantIdAndId(tenantId,organizationId).isEmpty())
            throw new AccessResourceNotFoundException("Organization not found");
    }
    private static UserAccessResponse response(UserRoleGrant g,String roleCode){
        return new UserAccessResponse(g.getId(),g.getTenantId(),g.getUserId(),g.getRoleId(),roleCode,
                g.getOrganizationId(),g.getStatus(),g.getCreatedAt(),g.getUpdatedAt());
    }
}
