# CRM-009 Finalization Workflow Status

- Source SHA: `53e71669482f5551edd90b9ffc8b5d3efb994839`
- Run ID: `30103845695`
- Result: FAIL

## Stage exit codes
```text
compile=0
openapi=0
backend_tests=1
npm_install=0
generate_types=0
web_lint=0
```

## Failure tail
```text
=== compile ===
=== openapi ===
=== backend_tests ===
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_command_executions_request_fk FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_command_executions_status_ck CHECK (
        execution_status IN ('PENDING','EXECUTING','EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME')
    ),
    CONSTRAINT crm_command_executions_terminal_ck CHECK (
        (execution_status NOT IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_command_executions_non_terminal_ck CHECK (
        (execution_status IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_command_executions_decision_uq UNIQUE (tenant_id, decision_id),
    CONSTRAINT crm_command_executions_idempotency_uq UNIQUE (tenant_id, idempotency_key)
)
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS crm_integration_command_executions_tenant_status_idx
    ON crm_integration_command_executions (tenant_id, execution_status, created_at)
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS crm_integration_command_executions_request_idx
    ON crm_integration_command_executions (tenant_id, integration_request_id, created_at)
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS crm_integration_outbox_event_claim_idx
    ON crm_integration_outbox (event_type, dispatch_status, next_attempt_at, created_at)
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.047 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: SELECT 1
2026-07-24 15:07:27.048 INFO  [main] o.f.c.i.s.DefaultSqlScriptExecutor - +----------+
| ?column? |
+----------+
| 1        |
+----------+

2026-07-24 15:07:27.048 DEBUG [main] o.f.core.internal.command.DbMigrate - Successfully completed migration of schema "public" to version "20260724.1 - create crm command executions ledger"
2026-07-24 15:07:27.048 DEBUG [main] o.f.c.i.s.JdbcTableSchemaHistory - Schema History table "public"."flyway_schema_history" successfully updated to reflect changes
2026-07-24 15:07:27.049 DEBUG [main] o.f.c.a.c.ClassicConfiguration - CherryPickConfigurationExtension not found
2026-07-24 15:07:27.050 DEBUG [main] o.f.core.internal.parser.Parser - Parsing V20260724_2__create_crm_command_artifacts.sql ...
2026-07-24 15:07:27.051 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 2: -- H2 compatibility migration for CRM-009 V20260724.2
CREATE TABLE IF NOT EXISTS crm_integration_command_artifacts (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    artifact_type VARCHAR(80) NOT NULL,
    artifact_id UUID NOT NULL,
    execution_status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT crm_command_artifacts_status_ck CHECK (
        execution_status IN ('CREATED', 'REVERSED')
    ),
    CONSTRAINT crm_command_artifacts_decision_action_uq
        UNIQUE (tenant_id, decision_id, action_code),
    CONSTRAINT crm_command_artifacts_tenant_uq UNIQUE (tenant_id, id)
)
2026-07-24 15:07:27.051 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 19: CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_tenant_decision_idx
    ON crm_integration_command_artifacts (tenant_id, decision_id)
2026-07-24 15:07:27.051 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 21: CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_artifact_idx
    ON crm_integration_command_artifacts (tenant_id, artifact_type, artifact_id)
2026-07-24 15:07:27.051 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 24: CREATE TABLE IF NOT EXISTS service_callback_replay (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_name VARCHAR(120) NOT NULL,
    jti VARCHAR(200) NOT NULL,
    nonce VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT service_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT service_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT service_callback_replay_nonce_uq UNIQUE (service_name, nonce)
)
2026-07-24 15:07:27.051 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 37: CREATE INDEX IF NOT EXISTS service_callback_replay_expiry_idx
    ON service_callback_replay (expires_at)
2026-07-24 15:07:27.052 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 39: CREATE INDEX IF NOT EXISTS service_callback_replay_tenant_received_idx
    ON service_callback_replay (tenant_id, received_at DESC)
2026-07-24 15:07:27.052 DEBUG [main] o.f.c.i.sqlscript.ParserSqlScript - Found statement at line 41: SELECT 1
2026-07-24 15:07:27.052 DEBUG [main] o.f.core.internal.command.DbMigrate - Starting migration of schema "public" to version "20260724.2 - create crm command artifacts" ...
2026-07-24 15:07:27.052 INFO  [main] o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "20260724.2 - create crm command artifacts"
2026-07-24 15:07:27.052 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: -- H2 compatibility migration for CRM-009 V20260724.2
CREATE TABLE IF NOT EXISTS crm_integration_command_artifacts (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    artifact_type VARCHAR(80) NOT NULL,
    artifact_id UUID NOT NULL,
    execution_status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT crm_command_artifacts_status_ck CHECK (
        execution_status IN ('CREATED', 'REVERSED')
    ),
    CONSTRAINT crm_command_artifacts_decision_action_uq
        UNIQUE (tenant_id, decision_id, action_code),
    CONSTRAINT crm_command_artifacts_tenant_uq UNIQUE (tenant_id, id)
)
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_tenant_decision_idx
    ON crm_integration_command_artifacts (tenant_id, decision_id)
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_artifact_idx
    ON crm_integration_command_artifacts (tenant_id, artifact_type, artifact_id)
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.053 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE TABLE IF NOT EXISTS service_callback_replay (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_name VARCHAR(120) NOT NULL,
    jti VARCHAR(200) NOT NULL,
    nonce VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT service_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT service_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT service_callback_replay_nonce_uq UNIQUE (service_name, nonce)
)
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS service_callback_replay_expiry_idx
    ON service_callback_replay (expires_at)
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: CREATE INDEX IF NOT EXISTS service_callback_replay_tenant_received_idx
    ON service_callback_replay (tenant_id, received_at DESC)
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - 0 rows affected
2026-07-24 15:07:27.054 DEBUG [main] o.f.c.i.s.DefaultSqlScriptExecutor - Executing SQL: SELECT 1
2026-07-24 15:07:27.054 INFO  [main] o.f.c.i.s.DefaultSqlScriptExecutor - +----------+
| ?column? |
+----------+
| 1        |
+----------+

2026-07-24 15:07:27.054 DEBUG [main] o.f.core.internal.command.DbMigrate - Successfully completed migration of schema "public" to version "20260724.2 - create crm command artifacts"
2026-07-24 15:07:27.055 DEBUG [main] o.f.c.i.s.JdbcTableSchemaHistory - Schema History table "public"."flyway_schema_history" successfully updated to reflect changes
2026-07-24 15:07:27.055 DEBUG [main] o.f.c.a.c.ClassicConfiguration - CherryPickConfigurationExtension not found
2026-07-24 15:07:27.062 INFO  [main] o.f.core.internal.command.DbMigrate - Successfully applied 52 migrations to schema "public", now at version v20260724.2 (execution time 00:00.528s)
2026-07-24 15:07:27.064 DEBUG [main] org.flywaydb.core.FlywayExecutor - Memory usage: 54 of 116M
2026-07-24 15:07:27.229 INFO  [main] o.h.jpa.internal.util.LogHelper - HHH000204: Processing PersistenceUnitInfo [name: default]
2026-07-24 15:07:27.277 INFO  [main] org.hibernate.Version - HHH000412: Hibernate ORM core version 6.6.29.Final
2026-07-24 15:07:27.308 INFO  [main] o.h.c.i.RegionFactoryInitiator - HHH000026: Second-level cache disabled
2026-07-24 15:07:27.407 INFO  [main] o.s.o.j.p.SpringPersistenceUnitInfo - No LoadTimeWeaver setup: ignoring JPA class transformer
2026-07-24 15:07:27.449 WARN  [main] org.hibernate.orm.deprecation - HHH90000025: H2Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-24 15:07:27.461 INFO  [main] o.hibernate.orm.connections.pooling - HHH10001005: Database info:
	Database JDBC URL [Connecting through datasource 'HikariDataSource (SanadHikariCP)']
	Database driver: undefined/unknown
	Database version: 2.3.232
	Autocommit mode: undefined/unknown
	Isolation level: undefined/unknown
	Minimum pool size: undefined/unknown
	Maximum pool size: undefined/unknown
2026-07-24 15:07:28.264 INFO  [main] o.h.e.t.j.p.i.JtaPlatformInitiator - HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-07-24 15:07:28.266 INFO  [main] o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-07-24 15:07:28.697 INFO  [main] o.s.d.j.r.query.QueryEnhancerFactory - Hibernate is in classpath; If applicable, HQL parser will be used.
2026-07-24 15:07:31.102 INFO  [main] c.s.platform.config.CorsProperties - CORS allowed origins (production=false): [https://snad-app.vercel.app]
2026-07-24 15:07:31.715 WARN  [main] o.s.b.a.s.s.UserDetailsServiceAutoConfiguration - 

Using generated security password: b14f4e89-50e8-4059-b5a1-3f969451ffad

This generated password is for development use only. Your security configuration must be updated before running your application in production.

2026-07-24 15:07:31.725 INFO  [main] o.s.s.c.a.a.c.InitializeUserDetailsBeanManagerConfigurer$InitializeUserDetailsManagerConfigurer - Global AuthenticationManager configured with UserDetailsService bean with name inMemoryUserDetailsManager
2026-07-24 15:07:32.321 INFO  [main] o.s.b.a.e.web.EndpointLinksResolver - Exposing 6 endpoints beneath base path '/actuator'
2026-07-24 15:07:32.417 INFO  [main] c.s.p.s.c.CircuitBreakerPolicyConfig - Circuit breaker policy configured: 5 breakers (database, redis, aiInference, emailProvider, webhookDelivery)
2026-07-24 15:07:32.428 INFO  [main] c.s.p.s.c.TimeoutAndRetryPolicyConfig - Timeout and retry policy configured: idempotent retry (maxAttempts=3, backoff=1s/2s/4s)
2026-07-24 15:07:33.228 INFO  [main] o.s.b.a.h.H2ConsoleAutoConfiguration - H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:sanad'
2026-07-24 15:07:33.272 DEBUG [main] c.s.p.scale.api.RateLimitFilter - Filter 'rateLimitFilter' configured for use
2026-07-24 15:07:33.272 INFO  [main] o.s.b.t.m.w.SpringBootMockServletContext - Initializing Spring TestDispatcherServlet ''
2026-07-24 15:07:33.273 INFO  [main] o.s.t.w.s.TestDispatcherServlet - Initializing Servlet ''
2026-07-24 15:07:33.274 INFO  [main] o.s.t.w.s.TestDispatcherServlet - Completed initialization in 1 ms
2026-07-24 15:07:33.355 INFO  [main] c.s.p.api.PlatformApiCountTest - Started PlatformApiCountTest in 10.974 seconds (process running for 13.84)
2026-07-24 15:07:33.425 INFO  [main] SANAD-STARTUP - SANAD Platform started: git_commit=unknown implementation_version=unknown
2026-07-24 15:07:35.522 INFO  [main] o.s.api.AbstractOpenApiResource - Init duration for springdoc-openapi is: 2022 ms
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.80 s -- in com.sanad.platform.api.PlatformApiCountTest
[INFO] 
[INFO] Results:
[INFO] 
[ERROR] Failures: 
[ERROR]   CrmOpenApiContractTest.specDefinesCoreCrm007AndIntegrationSchemas:119 OpenAPI spec is missing generated schema: CrmIntegrationControllerAiRequest ==> expected: not <null>
[INFO] 
[ERROR] Tests run: 30, Failures: 1, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  25.116 s
[INFO] Finished at: 2026-07-24T15:07:36Z
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.4:test (default-test) on project sanad-platform: There are test failures.
[ERROR] 
[ERROR] See /home/runner/work/SNAD/SNAD/apps/sanad-platform/target/surefire-reports for the individual test results.
[ERROR] See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream.
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
=== npm_install ===
=== generate_types ===
=== web_lint ===
```
