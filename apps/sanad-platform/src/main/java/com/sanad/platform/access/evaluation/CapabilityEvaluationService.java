package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideRepository;
import com.sanad.platform.access.relationship.RelationshipResolver;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.authorization.AuthorizationDecisionCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CapabilityEvaluationService {
    private static final String PIPELINE_ENABLED_PROPERTY="sanad.uac.pipeline-enabled";
    private static final String MODE_PROPERTY="sanad.uac.mode";

    private final UserRoleGrantService grantService; private final RoleService roleService;
    private final RoleCapabilityService roleCapabilityService; private final AccessCapabilityService capabilityService;
    private final OrganizationRepository organizationRepository; private final UserPermissionOverrideRepository overrideRepository;
    private final RelationshipResolver relationshipResolver; private final Environment environment;
    private AuthorizationDecisionCache decisionCache; private AuthorizationVersionService authorizationVersionService;

    public CapabilityEvaluationService(UserRoleGrantService grantService,RoleService roleService,
            RoleCapabilityService roleCapabilityService,AccessCapabilityService capabilityService,
            OrganizationRepository organizationRepository,UserPermissionOverrideRepository overrideRepository,
            RelationshipResolver relationshipResolver,Environment environment){
        this.grantService=grantService;this.roleService=roleService;this.roleCapabilityService=roleCapabilityService;
        this.capabilityService=capabilityService;this.organizationRepository=organizationRepository;
        this.overrideRepository=overrideRepository;this.relationshipResolver=relationshipResolver;this.environment=environment;
    }
    @Autowired(required=false)
    void setDecisionCache(AuthorizationDecisionCache decisionCache){this.decisionCache=decisionCache;}
    @Autowired(required=false)
    void setAuthorizationVersionService(AuthorizationVersionService authorizationVersionService){this.authorizationVersionService=authorizationVersionService;}

    @Transactional(readOnly=true)
    public AccessDecisionResponse evaluate(UUID tenantId,UUID userId,String capabilityCode,UUID organizationId){
        if(pipelineEnabled()){
            AuthorizationDecision d=evaluateDetailed(tenantId,userId,capabilityCode,organizationId);
            return new AccessDecisionResponse(tenantId,userId,organizationId,d.capability(),"ALLOW".equals(d.decision()),mapReason(d.reason()),d.matchedRoleId(),d.matchedRoleCode());
        }
        return evaluateLegacy(tenantId,userId,capabilityCode,organizationId);
    }
    private boolean pipelineEnabled(){return "true".equalsIgnoreCase(environment.getProperty(PIPELINE_ENABLED_PROPERTY,"false"))&&"authoritative".equalsIgnoreCase(environment.getProperty(MODE_PROPERTY,"legacy"));}
    private static String mapReason(String reason){return reason;}

    private AccessDecisionResponse evaluateLegacy(UUID tenantId,UUID userId,String capabilityCode,UUID organizationId){
        validateOrganization(tenantId,organizationId);AccessCapability capability;
        try{capability=capabilityService.loadByCode(capabilityCode);}catch(AccessResourceNotFoundException e){return denied(tenantId,userId,organizationId,normalize(capabilityCode),"CAPABILITY_NOT_FOUND");}
        if(capability.getStatus()!=CapabilityStatus.ACTIVE)return denied(tenantId,userId,organizationId,capability.getCode(),"CAPABILITY_INACTIVE");
        for(UserRoleGrant grant:grantService.activeGrants(tenantId,userId)){
            if(!scopeMatches(grant,organizationId))continue;Role role=roleService.load(tenantId,grant.getRoleId());if(role.getStatus()!=RoleStatus.ACTIVE)continue;
            if(roleCapabilityService.roleHasCapability(tenantId,role.getId(),capability.getId()))return new AccessDecisionResponse(tenantId,userId,organizationId,capability.getCode(),true,"ROLE_CAPABILITY_MATCH",role.getId(),role.getCode());
        }
        return denied(tenantId,userId,organizationId,capability.getCode(),"NO_MATCHING_ACTIVE_ROLE");
    }

    /** Five-stage Revision-D pipeline. Cache is advisory only and version-keyed. */
    @Transactional(readOnly=true)
    public AuthorizationDecision evaluateDetailed(UUID tenantId,UUID userId,String capabilityCode,UUID organizationId){
        if(tenantId==null||userId==null||decisionCache==null||authorizationVersionService==null){
            return evaluateDetailedUncached(tenantId,userId,capabilityCode,organizationId);
        }
        try{
            long version=authorizationVersionService.current(tenantId,userId);
            return decisionCache.getOrEvaluate(tenantId,userId,version,normalize(capabilityCode),organizationId,
                    ()->evaluateDetailedUncached(tenantId,userId,capabilityCode,organizationId));
        }catch(RuntimeException cacheStateUncertain){
            // Cache/version uncertainty may reduce performance, never expand access.
            return evaluateDetailedUncached(tenantId,userId,capabilityCode,organizationId);
        }
    }

    private AuthorizationDecision evaluateDetailedUncached(UUID tenantId,UUID userId,String capabilityCode,UUID organizationId){
        List<String> trace=new ArrayList<>();UUID decisionId=UUID.randomUUID();String normalized=normalize(capabilityCode);
        if(tenantId==null||userId==null)return deny(decisionId,normalized,"SUBJECT_CONTEXT_REQUIRED",trace);
        trace.add("STAGE_A:subject-ok");
        try{validateOrganization(tenantId,organizationId);}catch(AccessResourceNotFoundException e){return deny(decisionId,normalized,"ORGANIZATION_NOT_FOUND",trace);}trace.add("STAGE_A:tenant-boundary-ok");
        AccessCapability capability;try{capability=capabilityService.loadByCode(capabilityCode);}catch(AccessResourceNotFoundException e){return deny(decisionId,normalized,"CAPABILITY_NOT_FOUND",trace);}
        if(capability.getStatus()!=CapabilityStatus.ACTIVE)return deny(decisionId,capability.getCode(),"CAPABILITY_INACTIVE",trace);
        trace.add("STAGE_A:capability-active:"+capability.getCode());

        Instant now=Instant.now();
        List<UserPermissionOverrideRepository.ActiveOverride> activeOverrides=overrideRepository.findOverrides(tenantId,userId,capability.getId()).stream().filter(o->isActive(o,now)).toList();
        if(activeOverrides.stream().anyMatch(o->"DENY".equals(o.effect()))){
            trace.add("STAGE_B:explicit-direct-deny-active");
            return new AuthorizationDecision("DENY",capability.getCode(),DecisionSource.EXPLICIT_DENY,"EXPLICIT_DIRECT_DENY",null,null,null,null,decisionId,Instant.now(),trace);
        }
        trace.add("STAGE_B:no-active-direct-deny");

        List<Candidate> candidates=new ArrayList<>();
        for(UserPermissionOverrideRepository.ActiveOverride o:activeOverrides)if("ALLOW".equals(o.effect()))candidates.add(new Candidate(DecisionSource.EXPLICIT_ALLOW,"EXPLICIT_ALLOW_MATCH",o.scopeType(),o.scopeReference(),null,null));
        for(UserRoleGrant grant:grantService.activeGrants(tenantId,userId)){
            if(!scopeMatches(grant,organizationId))continue;Role role=roleService.load(tenantId,grant.getRoleId());if(role.getStatus()!=RoleStatus.ACTIVE)continue;
            if(roleCapabilityService.roleHasCapability(tenantId,role.getId(),capability.getId()))candidates.add(new Candidate(DecisionSource.ROLE_GRANT,"ROLE_CAPABILITY_MATCH","TENANT_ALL",null,role.getId(),role.getCode()));
        }
        relationshipResolver.resolveRelationshipGrant(tenantId,userId,capability.getId(),organizationId)
                .ifPresent(g->candidates.add(new Candidate(DecisionSource.RELATIONSHIP_POLICY,"RELATIONSHIP_POLICY_MATCH",g.scopeType(),g.objectId(),null,null)));
        trace.add(candidates.isEmpty()?"STAGE_C:no-candidate":"STAGE_C:candidates:"+candidates.size());

        List<Candidate> matching=candidates.stream().filter(c->scopeMatches(c,tenantId,userId,organizationId)).toList();
        for(Candidate c:matching)trace.add("STAGE_D:match:"+c.source()+":"+c.scopeType());
        if(matching.isEmpty())trace.add("STAGE_D:no-scope-match");
        if(!matching.isEmpty()){
            Candidate w=matching.get(0);trace.add("STAGE_E:allow");
            return new AuthorizationDecision("ALLOW",capability.getCode(),w.source(),w.reason(),w.matchedRoleId(),w.matchedRoleCode(),w.scopeType(),w.policy(),decisionId,Instant.now(),trace);
        }
        return deny(decisionId,capability.getCode(),"NO_MATCHING_ACTIVE_ROLE",trace);
    }

    private void validateOrganization(UUID tenantId,UUID organizationId){if(organizationId!=null&&organizationRepository.findByTenantIdAndId(tenantId,organizationId).isEmpty())throw new AccessResourceNotFoundException("Organization not found");}
    private static boolean scopeMatches(UserRoleGrant grant,UUID organizationId){if(organizationId==null)return grant.isTenantWide();return grant.isTenantWide()||organizationId.equals(grant.getOrganizationId());}
    private boolean scopeMatches(Candidate c,UUID tenantId,UUID userId,UUID organizationId){if("TENANT_ALL".equals(c.scopeType()))return true;if(c.scopeReference()!=null&&organizationId!=null&&c.scopeReference().equals(organizationId))return true;if(c.scopeType()==null)return false;return relationshipResolver.supportsRelationshipScope(tenantId,userId,c.scopeType(),c.scopeReference());}
    private static boolean isActive(UserPermissionOverrideRepository.ActiveOverride o,Instant now){return(o.validFrom()==null||!o.validFrom().isAfter(now))&&(o.validUntil()==null||o.validUntil().isAfter(now));}
    private static AuthorizationDecision deny(UUID decisionId,String capabilityCode,String reason,List<String> trace){trace.add("STAGE_E:default-deny:"+reason);return new AuthorizationDecision("DENY",capabilityCode,DecisionSource.DEFAULT_DENY,reason,null,null,null,null,decisionId,Instant.now(),trace);}
    private record Candidate(DecisionSource source,String reason,String scopeType,UUID scopeReference,UUID matchedRoleId,String matchedRoleCode){private String policy(){return null;}}
    private static AccessDecisionResponse denied(UUID tenantId,UUID userId,UUID organizationId,String code,String reason){return new AccessDecisionResponse(tenantId,userId,organizationId,code,false,reason,null,null);}
    private static String normalize(String value){return value==null?null:value.trim().toUpperCase(Locale.ROOT);}
}
