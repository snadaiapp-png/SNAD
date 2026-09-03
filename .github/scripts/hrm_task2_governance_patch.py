from pathlib import Path

EXPECTED_HEAD = "ddc47ecb134eddf81e685a05c8e7d83bd19e83fc"


def replace_exact(path: str, old: str, new: str, expected: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} matches, found {count} for {old!r}")
    p.write_text(text.replace(old, new))


flyway_history = "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmFlywayHistoryAssertionTest.java"
replace_exact(
    flyway_history,
    '            , "20260902.1"   // hrm-g0 master task 4 ws3+ws4 foundation schema\n    );',
    '            , "20260902.1"   // hrm-g0 master task 4 ws3+ws4 foundation schema\n'
    '            , "20260903.1"   // hrm-g0 master task 4 task 2 employment jurisdiction periods\n    );',
)

crm_acceptance = "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/Crm008bFoundationAcceptanceTest.java"
replace_exact(
    crm_acceptance,
    'private static final String CRM_LATEST_VERSION = "20260902.1";',
    'private static final String CRM_LATEST_VERSION = "20260903.1";',
)

crm_pg = "apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmPostgresMigrationTest.java"
replace_exact(
    crm_pg,
    '    private static final String WS3_WS4_FOUNDATION_VERSION = "20260902.1";\n'
    '    private static final String LATEST_MIGRATION_VERSION = WS3_WS4_FOUNDATION_VERSION;',
    '    private static final String WS3_WS4_FOUNDATION_VERSION = "20260902.1";\n'
    '    private static final String EMPLOYMENT_JURISDICTION_PERIODS_VERSION = "20260903.1";\n'
    '    private static final String LATEST_MIGRATION_VERSION = EMPLOYMENT_JURISDICTION_PERIODS_VERSION;',
)
replace_exact(
    crm_pg,
    '                        MigrationVersion.fromVersion(WS3_WS4_FOUNDATION_VERSION));',
    '                        MigrationVersion.fromVersion(WS3_WS4_FOUNDATION_VERSION),\n'
    '                        MigrationVersion.fromVersion(EMPLOYMENT_JURISDICTION_PERIODS_VERSION));',
    expected=2,
)

hr_rls = "apps/sanad-platform/src/test/java/com/sanad/platform/hr/rls/HrRlsFailClosedIntegrationTest.java"
replace_exact(
    hr_rls,
    '                "hr_employment_status_periods",\n',
    '                "hr_employment_status_periods",\n'
    '                "hr_employment_jurisdiction_periods",\n',
)
replace_exact(
    hr_rls,
    '            case "hr_employment_status_periods" -> insertHrEmployee(tenantId);\n',
    '            case "hr_employment_status_periods" -> insertHrEmployee(tenantId);\n'
    '            case "hr_employment_jurisdiction_periods" -> insertHrEmploymentJurisdictionPeriod(tenantId);\n',
)
replace_exact(
    hr_rls,
    '    private void insertHrMigrationTenantState(UUID tenantId) throws Exception {',
    '''    private void insertHrEmploymentJurisdictionPeriod(UUID tenantId) throws Exception {
        UUID employmentId = insertHrEmployee(tenantId, "EMP-JUR");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employment_jurisdiction_periods " +
                "(tenant_id, employment_id, labor_jurisdiction, effective_from, approval_status, approval_reference) " +
                "VALUES (?, ?, 'SA', DATE '2026-01-01', 'APPROVED', 'RLS-TEST')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            ps.executeUpdate();
        }
    }

    private void insertHrMigrationTenantState(UUID tenantId) throws Exception {''',
)

for path in (flyway_history, crm_acceptance, crm_pg, hr_rls):
    print(path)
