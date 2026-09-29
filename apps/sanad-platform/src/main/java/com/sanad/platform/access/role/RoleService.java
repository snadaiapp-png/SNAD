package com.sanad.platform.access.role;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.security.authorization.AuthorizationMutationCoordinator;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private AccessMutationAuditSupport audit;
    private AuthorizationMutationCoordinator authChanges;

    public RoleService(RoleRepository roleRepository, TenantRepository tenantRepository) {
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
    }
    @Autowired(required=false) void setAudit(AccessMutationAuditSupport audit){this.audit=audit;}
    @Autowired(required=false) void setAuthChanges(AuthorizationMutationCoordinator authChanges){this.authChanges=authChanges;}

    @Transactional
    public RoleResponse create(UUID tenantId, CreateRoleRequest request) {
        requireTenant(tenantId);
        String code=normalizeCode(request.code());
        if(roleRepository.existsByTenantIdAndCode(tenantId,code)) throw new AccessConflictException("Role code already exists in tenant: "+code);
        Role saved=roleRepository.save(new Role(tenantId,code,request.name(),request.description()));
        audit(tenantId,"ROLE_CREATE",saved.getId(),null,Map.of("code",saved.getCode(),"status",saved.getStatus().name()));
        return RoleResponse.from(saved);
    }
    @Transactional(readOnly=true) public List<RoleResponse> list(UUID tenantId){requireTenant(tenantId);return roleRepository.findByTenantIdOrderByCodeAsc(tenantId).stream().map(RoleResponse::from).toList();}
    @Transactional(readOnly=true) public RoleResponse get(UUID tenantId,UUID roleId){return RoleResponse.from(load(tenantId,roleId));}

    @Transactional
    public RoleResponse update(UUID tenantId,UUID roleId,UpdateRoleRequest request){
        Role role=load(tenantId,roleId);
        Map<String,Object> before=Map.of("name",safe(role.getName()),"description",safe(role.getDescription()));
        role.setName(request.name());role.setDescription(request.description());
        Role saved=roleRepository.save(role);
        audit(tenantId,"ROLE_UPDATE",roleId,before,Map.of("name",safe(saved.getName()),"description",safe(saved.getDescription())));
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse changeStatus(UUID tenantId,UUID roleId,RoleStatus status){
        Role role=load(tenantId,roleId); String before=role.getStatus().name();
        role.setStatus(Objects.requireNonNull(status,"status must not be null"));
        Role saved=roleRepository.save(role);
        audit(tenantId,"ROLE_STATUS_CHANGE",roleId,Map.of("status",before),Map.of("status",saved.getStatus().name()));
        if(!before.equals(saved.getStatus().name())&&authChanges!=null) authChanges.roleChanged(tenantId,roleId,"ROLE_STATUS_CHANGED");
        return RoleResponse.from(saved);
    }

    public Role load(UUID tenantId,UUID roleId){
        Objects.requireNonNull(tenantId,"tenantId must not be null");Objects.requireNonNull(roleId,"roleId must not be null");
        return roleRepository.findByTenantIdAndId(tenantId,roleId).orElseThrow(()->new AccessResourceNotFoundException("Role not found"));
    }
    private void audit(UUID tenantId,String action,UUID id,Object before,Object after){if(audit!=null)audit.success(tenantId,action,"ROLE",id==null?null:id.toString(),before,after);}
    private static String safe(String v){return v==null?"":v;}
    private void requireTenant(UUID tenantId){Objects.requireNonNull(tenantId,"tenantId must not be null");if(!tenantRepository.existsById(tenantId))throw new AccessResourceNotFoundException("Tenant not found");}
    private static String normalizeCode(String code){return code==null?null:code.trim().toUpperCase(Locale.ROOT);}
}
