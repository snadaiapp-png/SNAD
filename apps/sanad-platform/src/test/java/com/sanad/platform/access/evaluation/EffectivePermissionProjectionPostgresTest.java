package com.sanad.platform.access.evaluation;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EffectivePermissionProjectionPostgresTest {
    private static final String SOURCE_URL=System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad");
    private static final String USER=System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad");
    private static final String PASSWORD=System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD","");

    @BeforeAll static void requirePostgres(){
        boolean available;try{available=Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("EffectivePermissionProjectionPostgresTest");}catch(Throwable ignored){available=false;}
        Assumptions.assumeTrue(available,"PostgreSQL Direct is required");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL,USER,PASSWORD);
    }

    @Test
    void rebuildPersistsRoleAndOverrideRowsAtCurrentAuthorizationVersionAndIsIdempotent() throws Exception {
        Flyway flyway=Flyway.configure().dataSource(isolatedUrl(),USER,PASSWORD).locations("classpath:db/migration").cleanDisabled(false).validateOnMigrate(true).load();
        flyway.clean();flyway.migrate();flyway.validate();
        try(Connection connection=DriverManager.getConnection(isolatedUrl(),USER,PASSWORD)){
            Fixture f=findFixture(connection);setTenant(connection,f.tenantId());
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,status,created_at,updated_at) VALUES (?,?,?,?, 'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){
                p.setObject(1,UUID.randomUUID());p.setObject(2,f.tenantId());p.setObject(3,f.userId());p.setObject(4,f.roleId());p.executeUpdate();
            }
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO user_permission_overrides (id,tenant_id,user_id,capability_id,effect,scope_type,scope_reference,reason,created_by) VALUES (?,?,?,?, 'ALLOW','TEAM',?,?,?)")){
                p.setObject(1,UUID.randomUUID());p.setObject(2,f.tenantId());p.setObject(3,f.userId());p.setObject(4,f.capabilityId());p.setObject(5,UUID.randomUUID());p.setString(6,"projection postgres proof");p.setObject(7,f.userId());p.executeUpdate();
            }
            try(PreparedStatement p=connection.prepareStatement("UPDATE users SET authorization_version=7 WHERE tenant_id=? AND id=?")){p.setObject(1,f.tenantId());p.setObject(2,f.userId());p.executeUpdate();}

            JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            Integer roleCapabilityCount=jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role_capabilities WHERE tenant_id=? AND role_id=?",
                    Integer.class,f.tenantId(),f.roleId());
            assertThat(roleCapabilityCount).isNotNull().isPositive();
            int expectedProjectionCount=roleCapabilityCount+1;

            EffectivePermissionProjectionService service=new EffectivePermissionProjectionService(jdbc,new AuthorizationVersionService(jdbc));
            List<EffectivePermissionProjectionService.EffectivePermissionRow> first=service.rebuild(f.tenantId(),f.userId());
            List<EffectivePermissionProjectionService.EffectivePermissionRow> second=service.rebuild(f.tenantId(),f.userId());

            assertThat(first).hasSize(expectedProjectionCount);
            assertThat(first.stream().filter(row->"ROLE".equals(row.source())).count()).isEqualTo(roleCapabilityCount.longValue());
            assertThat(first.stream().filter(row->"OVERRIDE".equals(row.source())).count()).isEqualTo(1L);
            assertThat(first).allMatch(row->row.authorizationVersion()==7L);

            assertThat(second).hasSize(expectedProjectionCount);
            assertThat(second.stream().filter(row->"ROLE".equals(row.source())).count()).isEqualTo(roleCapabilityCount.longValue());
            assertThat(second.stream().filter(row->"OVERRIDE".equals(row.source())).count()).isEqualTo(1L);
            assertThat(second).allMatch(row->row.authorizationVersion()==7L);

            Integer persistedCount=jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id=? AND user_id=?",
                    Integer.class,f.tenantId(),f.userId());
            assertThat(persistedCount).isEqualTo(expectedProjectionCount);

            Integer denyCount=jdbc.queryForObject("SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id=? AND user_id=? AND effect='DENY'",Integer.class,f.tenantId(),f.userId());
            assertThat(denyCount).isZero();
        }
    }

    private static Fixture findFixture(Connection c)throws Exception{
        try(PreparedStatement tenants=c.prepareStatement("SELECT id FROM tenants ORDER BY id");ResultSet tr=tenants.executeQuery()){
            while(tr.next()){
                UUID tenant=tr.getObject(1,UUID.class);setTenant(c,tenant);
                try(PreparedStatement p=c.prepareStatement("SELECT u.id,r.id,rc.capability_id FROM users u JOIN roles r ON r.tenant_id=u.tenant_id AND r.status='ACTIVE' JOIN role_capabilities rc ON rc.tenant_id=r.tenant_id AND rc.role_id=r.id WHERE u.tenant_id=? AND u.status='ACTIVE' ORDER BY u.id,r.id,rc.capability_id LIMIT 1")){
                    p.setObject(1,tenant);try(ResultSet r=p.executeQuery()){if(r.next())return new Fixture(tenant,r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class));}
                }
            }
        }
        throw new AssertionError("No tenant/user/role-capability fixture after migrations");
    }
    private static void setTenant(Connection c,UUID tenant)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")){p.setString(1,tenant.toString());p.executeQuery();}}
    private static String isolatedUrl(){return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);}
    private record Fixture(UUID tenantId,UUID userId,UUID roleId,UUID capabilityId){}
}
