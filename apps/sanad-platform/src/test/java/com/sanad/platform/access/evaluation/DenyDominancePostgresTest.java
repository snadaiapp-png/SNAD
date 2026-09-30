package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideJdbcRepository;
import com.sanad.platform.access.relationship.SubjectRelationshipResolver;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DenyDominancePostgresTest {
    private static final String SOURCE_URL=System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad");
    private static final String USER=System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad");
    private static final String PASSWORD=System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD","");

    @BeforeAll static void requirePostgres(){
        boolean available;try{available=Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("DenyDominancePostgresTest");}catch(Throwable ignored){available=false;}
        Assumptions.assumeTrue(available,"PostgreSQL Direct is required for DenyDominancePostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL,USER,PASSWORD);
    }

    @Test
    void capabilityWideDenyBeatsRoleDirectAllowRelationshipAndEveryScope() throws Exception {
        migrate();
        try(Connection connection=DriverManager.getConnection(isolatedUrl(),USER,PASSWORD)){
            UUID tenant=firstUuid(connection,"SELECT id FROM tenants ORDER BY id LIMIT 1");
            CapabilityFixture cap=firstCapability(connection);
            UUID subject=UUID.randomUUID();
            setTenant(connection,tenant);

            insertOverride(connection,tenant,subject,cap.id(),"DENY",null,null);
            List<String> scopes=List.of("SELF","OWN","DIRECT_REPORTS","REPORTING_TREE","TEAM","DEPARTMENT","ORG_UNIT","ORGANIZATION","BRANCH","BUSINESS_UNIT","LEGAL_ENTITY","PROJECT","TENANT_ALL");
            for(String scope:scopes){
                UUID ref="TENANT_ALL".equals(scope)?null:UUID.randomUUID();
                insertOverride(connection,tenant,subject,cap.id(),"ALLOW",scope,ref);
            }
            insertRelationship(connection,tenant,subject,tenant);

            JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            UserPermissionOverrideJdbcRepository overrides=new UserPermissionOverrideJdbcRepository(jdbc);
            SubjectRelationshipResolver relationships=new SubjectRelationshipResolver(jdbc);

            AccessCapability capability=mock(AccessCapability.class);
            when(capability.getId()).thenReturn(cap.id());when(capability.getCode()).thenReturn(cap.code());when(capability.getStatus()).thenReturn(CapabilityStatus.ACTIVE);
            AccessCapabilityService capabilityService=mock(AccessCapabilityService.class);when(capabilityService.loadByCode(cap.code())).thenReturn(capability);

            UUID roleId=UUID.randomUUID();
            UserRoleGrant grant=mock(UserRoleGrant.class);when(grant.isTenantWide()).thenReturn(true);when(grant.getRoleId()).thenReturn(roleId);
            UserRoleGrantService grants=mock(UserRoleGrantService.class);when(grants.activeGrants(tenant,subject)).thenReturn(List.of(grant));
            Role role=mock(Role.class);when(role.getId()).thenReturn(roleId);when(role.getCode()).thenReturn("TEST_ALLOW");when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
            RoleService roles=mock(RoleService.class);when(roles.load(tenant,roleId)).thenReturn(role);
            RoleCapabilityService roleCaps=mock(RoleCapabilityService.class);when(roleCaps.roleHasCapability(tenant,roleId,cap.id())).thenReturn(true);
            OrganizationRepository organizations=mock(OrganizationRepository.class);
            Environment environment=mock(Environment.class);

            CapabilityEvaluationService evaluator=new CapabilityEvaluationService(grants,roles,roleCaps,capabilityService,organizations,overrides,relationships,environment);
            AuthorizationDecision decision=evaluator.evaluateDetailed(tenant,subject,cap.code(),null);
            assertThat(decision.decision()).isEqualTo("DENY");
            assertThat(decision.source()).isEqualTo(DecisionSource.EXPLICIT_DENY);
            assertThat(decision.reason()).isEqualTo("EXPLICIT_DIRECT_DENY");
            assertThat(decision.trace()).contains("STAGE_B:explicit-direct-deny-active");
        }
    }

    @Test
    void scopedDenyIsRejectedByDatabaseCheck() throws Exception {
        migrate();
        try(Connection connection=DriverManager.getConnection(isolatedUrl(),USER,PASSWORD)){
            UUID tenant=firstUuid(connection,"SELECT id FROM tenants ORDER BY id LIMIT 1");CapabilityFixture cap=firstCapability(connection);setTenant(connection,tenant);
            SQLException failure=assertThrows(SQLException.class,()->insertOverride(connection,tenant,UUID.randomUUID(),cap.id(),"DENY","TEAM",UUID.randomUUID()));
            assertThat(failure.getSQLState()).isEqualTo("23514");
        }
    }

    private static void migrate(){Flyway f=Flyway.configure().dataSource(isolatedUrl(),USER,PASSWORD).locations("classpath:db/migration").cleanDisabled(false).validateOnMigrate(true).load();f.clean();f.migrate();f.validate();}
    private static String isolatedUrl(){return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);}
    private static void setTenant(Connection c,UUID tenant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")){p.setString(1,tenant.toString());p.executeQuery();}}
    private static UUID firstUuid(Connection c,String sql)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){assertThat(r.next()).isTrue();return r.getObject(1,UUID.class);}}
    private static CapabilityFixture firstCapability(Connection c)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id, code FROM access_capabilities WHERE status='ACTIVE' ORDER BY code LIMIT 1");ResultSet r=p.executeQuery()){assertThat(r.next()).isTrue();return new CapabilityFixture(r.getObject(1,UUID.class),r.getString(2));}}
    private static void insertOverride(Connection c,UUID tenant,UUID user,UUID capability,String effect,String scope,UUID ref)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO user_permission_overrides (id,tenant_id,user_id,capability_id,effect,scope_type,scope_reference,reason,created_by) VALUES (?,?,?,?,?,?,?,?,?)")){p.setObject(1,UUID.randomUUID());p.setObject(2,tenant);p.setObject(3,user);p.setObject(4,capability);p.setString(5,effect);p.setString(6,scope);p.setObject(7,ref);p.setString(8,"deny dominance postgres proof");p.setObject(9,UUID.randomUUID());p.executeUpdate();}}
    private static void insertRelationship(Connection c,UUID tenant,UUID user,UUID tenantObject)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO subject_relationships (id,tenant_id,subject_user_id,relationship_type,object_type,object_id,created_by) VALUES (?,?,?,?,?,?,?)")){p.setObject(1,UUID.randomUUID());p.setObject(2,tenant);p.setObject(3,user);p.setString(4,"ADMIN_OF");p.setString(5,"TENANT");p.setObject(6,tenantObject);p.setObject(7,UUID.randomUUID());p.executeUpdate();}}
    private record CapabilityFixture(UUID id,String code){}
}
