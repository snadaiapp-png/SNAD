#!/usr/bin/env python3
"""RLS-06 classification matrix — chain end-state + source-derived contracts.
Produces: classification matrix JSON/MD + the T1 table array for the remediation migration."""
import psycopg, json, re

conn = psycopg.connect("host=127.0.0.1 port=5433 dbname=sanad user=sanad password=sanad_local_test")
tables = conn.execute("""
SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity, pg_get_userbyid(c.relowner),
  EXISTS(SELECT 1 FROM information_schema.columns col WHERE col.table_schema='public'
         AND col.table_name=c.relname AND col.column_name='tenant_id')
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p') ORDER BY c.relname""").fetchall()

AUDIT_49 = set('access_capabilities application_iam_contracts applications billing_invoices bp_analytics_snapshots bp_inventory_balances bp_inventory_movements bp_ledger_entries bp_payment_events bp_process_runs bp_process_steps bp_workflow_approvals country_currencies flyway_schema_history hr_compliance_rules hr_country_packs login_tenant_index module_capabilities modules organization_memberships organizations partners password_reset_tokens plan_module_entitlements plan_versions platform_audit_logs platform_countries prices product_entitlements products protected_system_roles provisioning_job_steps provisioning_jobs refresh_tokens role_capabilities roles saas_plan_entitlements saas_plans service_callback_replay subscription_change_events subscription_commands subscription_items system_services tenant_domains tenant_quota tenant_subscriptions tenants usage_metrics user_role_assignments'.split())

T2_DEFER = {'refresh_tokens','password_reset_tokens','login_tenant_index','provisioning_jobs','provisioning_job_steps','subscription_commands'}

def classify(name, has_tenant, rls):
    if rls:
        return 'RLS-PROTECTED'  # already protected in chain end-state
    if name in T2_DEFER:
        return 'T2-DEFERRED-CONTRACT'
    if not has_tenant:
        return 'PLATFORM-REFERENCE'  # no tenant scoping; grants-based default-deny
    # tenant-owned business data classes (evidence: module migrations + CRM-018/CRM post-baseline contracts)
    for pfx in ('crm_','commerce_','erp_','bp_','website_','websites','subscription_items',
                'subscription_change_events','tenant_','billing_invoices','organizations',
                'organization_memberships','user_role_assignments','governance_configurations'):
        if name == pfx.rstrip('_') and pfx in ('websites',):
            return 'T1-TENANT-OWNED'
        if name.startswith(pfx) or (pfx.endswith('_') and name.startswith(pfx)) or name==pfx:
            return 'T1-TENANT-OWNED'
    return 'UNCLASSIFIED'

rows = []
counts = {}
t1 = []
for name, rls, force, owner, has_tenant in tables:
    cls = classify(name, has_tenant, rls)
    counts[cls] = counts.get(cls,0)+1
    if cls=='T1-TENANT-OWNED':
        t1.append(name)
    rows.append({'table':name,'rls':rls,'force':force,'owner':owner,'has_tenant_id':has_tenant,
                 'class':cls,'in_audit49':name in AUDIT_49})

# force owner check for T1
owners = {r['table']: r['owner'] for r in rows if r['class']=='T1-TENANT-OWNED'}
non_sanad_owner = {k:v for k,v in owners.items() if v!='sanad'}

out = {
 'total_tables': len(tables),
 'counts': counts,
 'audit49_classes': {},
 't1_table_array': sorted(t1),
 't1_count': len(t1),
 't1_non_sanad_owner': non_sanad_owner,
 'rows': rows,
}
# audit49 classification breakdown
for r in rows:
    if r['in_audit49']:
        out['audit49_classes'].setdefault(r['class'], []).append(r['table'])

with open('/home/z/my-project/evidence-rls/classification_matrix.json','w') as f:
    json.dump(out, f, indent=1)

print('counts:', json.dumps(counts, indent=1))
print('T1 count:', len(t1))
print('T1 non-sanad owner:', non_sanad_owner)
print('audit49 classes:')
for k,v in out['audit49_classes'].items():
    print(' ', k, len(v), '->', sorted(v)[:8], '...' if len(v)>8 else '')
print('UNCLASSIFIED:', [r['table'] for r in rows if r['class']=='UNCLASSIFIED'])
