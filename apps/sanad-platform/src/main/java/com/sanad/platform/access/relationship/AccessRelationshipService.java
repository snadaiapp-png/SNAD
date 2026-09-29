package com.sanad.platform.access.relationship;

import com.sanad.platform.access.evaluation.AuthorizationVersionService;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.authorization.AuthorizationMutationCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AccessRelationshipService {
    private static final Set<String> RELATIONSHIP_TYPES=Set.of("MANAGES","MEMBER_OF","BELONGS_TO","ADMIN_OF","PARTNER_MANAGES");
    private static final Set<String> OBJECT_TYPES=Set.of("EMPLOYEE","TEAM","DEPARTMENT","BRANCH","ORG_UNIT","TENANT","PARTNER");
    private final SubjectRelationshipRepository repository; private final AuthorizationVersionService authorizationVersionService;
    private final PlatformAuditWriter auditWriter; private final JdbcTemplate jdbc; private AuthorizationMutationCoordinator authChanges;
    public AccessRelationshipService(SubjectRelationshipRepository repository,AuthorizationVersionService authorizationVersionService,PlatformAuditWriter auditWriter,JdbcTemplate jdbc){this.repository=repository;this.authorizationVersionService=authorizationVersionService;this.auditWriter=auditWriter;this.jdbc=jdbc;}
    @Autowired(required=false) void setAuthChanges(AuthorizationMutationCoordinator authChanges){this.authChanges=authChanges;}

    @Transactional
    public RelationshipResponse create(UUID tenantId,UUID actorUserId,CreateRelationshipRequest request){
        if(tenantId==null||actorUserId==null||request==null)throw new IllegalArgumentException("SUBJECT_CONTEXT_REQUIRED");
        if(request.subjectUserId()==null||request.objectId()==null)throw new IllegalArgumentException("RELATIONSHIP_TARGET_REQUIRED");
        String relationshipType=normalize(request.relationshipType()),objectType=normalize(request.objectType());
        if(!RELATIONSHIP_TYPES.contains(relationshipType))throw new IllegalArgumentException("INVALID_RELATIONSHIP_TYPE");
        if(!OBJECT_TYPES.contains(objectType))throw new IllegalArgumentException("INVALID_RELATIONSHIP_OBJECT_TYPE");
        Integer userCount=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE tenant_id = ? AND id = ?",Integer.class,tenantId,request.subjectUserId());if(userCount==null||userCount!=1)throw new IllegalArgumentException("USER_TENANT_MISMATCH");
        if("TENANT".equals(objectType)&&!tenantId.equals(request.objectId()))throw new IllegalArgumentException("TENANT_OBJECT_MISMATCH");
        Instant validFrom=request.validFrom()==null?Instant.now():request.validFrom();if(request.validUntil()!=null&&!request.validUntil().isAfter(validFrom))throw new IllegalArgumentException("INVALID_VALIDITY_WINDOW");
        SubjectRelationshipRepository.RelationshipRow row=repository.insert(tenantId,request.subjectUserId(),relationshipType,objectType,request.objectId(),validFrom,request.validUntil(),"EXPLICIT",actorUserId);
        auditWriter.writeSuccess(tenantId,actorUserId,tenantId,"SUBJECT_RELATIONSHIP_CREATE","SUBJECT_RELATIONSHIP",row.id().toString(),request.reason(),null,Map.of("subjectUserId",row.subjectUserId(),"relationshipType",row.relationshipType(),"objectType",row.objectType(),"objectId",row.objectId()),null,Instant.now());
        writeEvent(tenantId,actorUserId,row.subjectUserId(),row.id(),"CREATE");long version=authorizationVersionService.bump(tenantId,row.subjectUserId());if(authChanges!=null)authChanges.publishOnly(tenantId,row.subjectUserId(),"SUBJECT_RELATIONSHIP_CHANGED",version);
        return response(row);
    }
    @Transactional(readOnly=true) public List<RelationshipResponse> list(UUID tenantId,UUID subjectUserId){return repository.listForUser(tenantId,subjectUserId).stream().map(AccessRelationshipService::response).toList();}

    @Transactional
    public void revoke(UUID tenantId,UUID actorUserId,UUID relationshipId,String reason){
        SubjectRelationshipRepository.RelationshipRow row=repository.findById(tenantId,relationshipId);if(!repository.revoke(tenantId,relationshipId))throw new IllegalArgumentException("RELATIONSHIP_NOT_ACTIVE");
        auditWriter.writeSuccess(tenantId,actorUserId,tenantId,"SUBJECT_RELATIONSHIP_REVOKE","SUBJECT_RELATIONSHIP",relationshipId.toString(),reason,Map.of("subjectUserId",row.subjectUserId(),"objectId",row.objectId()),Map.of("revoked",true),null,Instant.now());
        writeEvent(tenantId,actorUserId,row.subjectUserId(),relationshipId,"REVOKE");long version=authorizationVersionService.bump(tenantId,row.subjectUserId());if(authChanges!=null)authChanges.publishOnly(tenantId,row.subjectUserId(),"SUBJECT_RELATIONSHIP_CHANGED",version);
    }
    private void writeEvent(UUID tenantId,UUID actorUserId,UUID subjectUserId,UUID relationshipId,String operation){jdbc.update("INSERT INTO authorization_change_events (id, tenant_id, event_type, actor_user_id, target_type, target_id, payload) VALUES (?, ?, 'SUBJECT_RELATIONSHIP_CHANGED', ?, 'USER', ?, ?::jsonb)",UUID.randomUUID(),tenantId,actorUserId,subjectUserId,"{\"operation\":\""+operation+"\",\"relationshipId\":\""+relationshipId+"\"}");}
    private static RelationshipResponse response(SubjectRelationshipRepository.RelationshipRow row){return new RelationshipResponse(row.id(),row.subjectUserId(),row.relationshipType(),row.objectType(),row.objectId(),row.validFrom(),row.validUntil(),row.source());}
    private static String normalize(String value){return value==null?"":value.trim().toUpperCase();}
    public record CreateRelationshipRequest(UUID subjectUserId,String relationshipType,String objectType,UUID objectId,Instant validFrom,Instant validUntil,String reason){}
    public record RelationshipResponse(UUID id,UUID subjectUserId,String relationshipType,String objectType,UUID objectId,Instant validFrom,Instant validUntil,String source){}
}
