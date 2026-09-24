from pathlib import Path


def replace_exact(path: str, old: str, new: str, expected_count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    actual = text.count(old)
    if actual != expected_count:
        raise SystemExit(f"{path}: expected {expected_count} occurrences, found {actual}")
    p.write_text(text.replace(old, new))


# Runtime defect: HrLeaveService was writing a non-existent mini-schema into
# the canonical HR outbox. Align it with V20260905_14 (event/aggregate model).
replace_exact(
    "apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrLeaveService.java",
    r'''        jdbc.update(
                "INSERT INTO hr_domain_event_outbox (id, tenant_id, event_type, resource_type, resource_id, payload, created_at) " +
                "VALUES (?, ?, ?, 'LEAVE_REQUEST', ?, ?::jsonb, ?)",
                outboxId, tenantId, eventType, resourceId,
                "{\"eventType\":\"" + eventType + "\",\"resourceId\":\"" + resourceId + "\"}",
                Timestamp.from(now));''',
    r'''        jdbc.update(
                "INSERT INTO hr_domain_event_outbox " +
                "(event_id, tenant_id, event_type, aggregate_type, aggregate_id, actor_user_id, occurred_at, payload) " +
                "VALUES (?, ?, ?, 'LEAVE_REQUEST', ?, ?, ?, ?::jsonb)",
                outboxId, tenantId, eventType, resourceId, actorId, Timestamp.from(now),
                "{\"eventType\":\"" + eventType + "\",\"resourceId\":\"" + resourceId + "\"}");'''
)

# Flyway history contract: G2 adds eight legitimate forward-only versions.
replace_exact(
    "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmFlywayHistoryAssertionTest.java",
    '''            , "20260921.1"   // canonical project-owner permissions and control-plane identity
            , "20260922.1"   // hrm g1 canonical operator capabilities + admin tenant scopes
    );''',
    '''            , "20260921.1"   // canonical project-owner permissions and control-plane identity
            , "20260922.1"   // hrm g1 canonical operator capabilities + admin tenant scopes
            // HRM-G2 — time, attendance, timesheets, leave
            , "20260923.1"   // hr g2 time attendance leave schema
            , "20260923.2"   // hr g2 fail-closed rls policies
            , "20260923.3"   // hr g2 leave types + capability seed
            , "20260924.1"   // hr g2 scheduling schema
            , "20260924.2"   // hr g2 attendance events + leave ledger
            , "20260924.3"   // hr g2 workflow linkage
            , "20260924.4"   // hr g2 idempotency constraint
            , "20260924.5"   // hr g2 leave state canonical alignment
    );'''
)

replace_exact(
    "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/Crm008bFoundationAcceptanceTest.java",
    '''    //   V20260922_1 = HRM-G1 canonical operator capabilities + ADMIN tenant scopes
    private static final String CRM_LATEST_VERSION = "20260922.1"; // terminal versioned migration''',
    '''    //   V20260922_1 = HRM-G1 canonical operator capabilities + ADMIN tenant scopes
    //   V20260923_1..3 + V20260924_1..5 = HRM-G2 canonical migration chain
    private static final String CRM_LATEST_VERSION = "20260924.5"; // terminal versioned migration'''
)

crm_postgres = "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmPostgresMigrationTest.java"
replace_exact(
    crm_postgres,
    '''    private static final String HR_G1_OPERATOR_CAPABILITIES_VERSION = "20260922.1";
    private static final String LATEST_MIGRATION_VERSION = HR_G1_OPERATOR_CAPABILITIES_VERSION;''',
    '''    private static final String HR_G1_OPERATOR_CAPABILITIES_VERSION = "20260922.1";
    private static final String HR_G2_TIME_ATTENDANCE_LEAVE_SCHEMA_VERSION = "20260923.1";
    private static final String HR_G2_RLS_POLICIES_VERSION = "20260923.2";
    private static final String HR_G2_SEED_LEAVE_TYPES_CAPABILITIES_VERSION = "20260923.3";
    private static final String HR_G2_SCHEDULING_SCHEMA_VERSION = "20260924.1";
    private static final String HR_G2_ATTENDANCE_EVENTS_LEDGER_VERSION = "20260924.2";
    private static final String HR_G2_WORKFLOW_LINKAGE_VERSION = "20260924.3";
    private static final String HR_G2_IDEMPOTENCY_CONSTRAINT_VERSION = "20260924.4";
    private static final String HR_G2_LEAVE_STATE_CANONICAL_ALIGNMENT_VERSION = "20260924.5";
    private static final String LATEST_MIGRATION_VERSION = HR_G2_LEAVE_STATE_CANONICAL_ALIGNMENT_VERSION;'''
)
replace_exact(
    crm_postgres,
    '''                        MigrationVersion.fromVersion(HR_G1_OPERATOR_CAPABILITIES_VERSION));''',
    '''                        MigrationVersion.fromVersion(HR_G1_OPERATOR_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(HR_G2_TIME_ATTENDANCE_LEAVE_SCHEMA_VERSION),
                        MigrationVersion.fromVersion(HR_G2_RLS_POLICIES_VERSION),
                        MigrationVersion.fromVersion(HR_G2_SEED_LEAVE_TYPES_CAPABILITIES_VERSION),
                        MigrationVersion.fromVersion(HR_G2_SCHEDULING_SCHEMA_VERSION),
                        MigrationVersion.fromVersion(HR_G2_ATTENDANCE_EVENTS_LEDGER_VERSION),
                        MigrationVersion.fromVersion(HR_G2_WORKFLOW_LINKAGE_VERSION),
                        MigrationVersion.fromVersion(HR_G2_IDEMPOTENCY_CONSTRAINT_VERSION),
                        MigrationVersion.fromVersion(HR_G2_LEAVE_STATE_CANONICAL_ALIGNMENT_VERSION));''',
    expected_count=2,
)

replace_exact(
    "apps/sanad-platform/src/test/java/com/sanad/platform/hr/recruitment/db/HrG1MigrationTest.java",
    '    static final String REPOSITORY_LEDGER_HEAD_VERSION = "20260922.1";',
    '    static final String REPOSITORY_LEDGER_HEAD_VERSION = "20260924.5";',
)

# Keep the RLS inventory strict; extend it instead of weakening discovery/assertions.
replace_exact(
    "apps/sanad-platform/src/test/java/com/sanad/platform/hr/rls/HrRlsFailClosedIntegrationTest.java",
    '''                "hr_tenant_policies"
        );''',
    '''                "hr_tenant_policies",
                // G2 time, attendance, scheduling, timesheets and leave — all tenant-scoped.
                "hr_attendance_events",
                "hr_attendance_records",
                "hr_g2_idempotency_records",
                "hr_leave_balances",
                "hr_leave_ledger_entries",
                "hr_leave_policies",
                "hr_leave_requests",
                "hr_leave_types",
                "hr_schedule_assignments",
                "hr_schedule_versions",
                "hr_timesheet_entries",
                "hr_timesheets",
                "hr_work_schedules"
        );'''
)

replace_exact(
    "apps/sanad-platform/src/test/java/com/sanad/platform/subscription/billing/R0C13G02SchemaPostgresTest.java",
    '            assertThat(rs.getString(1)).isEqualTo("20260922.1");',
    '            assertThat(rs.getString(1)).isEqualTo("20260924.5");',
)

# Local source-level invariants before the commit is allowed to leave the runner.
leave_text = Path("apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrLeaveService.java").read_text()
if "hr_domain_event_outbox (id," in leave_text:
    raise SystemExit("stale non-canonical HR outbox insert still present")
if "event_id, tenant_id, event_type, aggregate_type, aggregate_id, actor_user_id, occurred_at, payload" not in leave_text:
    raise SystemExit("canonical HR outbox insert not present")

print("G2 canonical repair replacements applied successfully")
