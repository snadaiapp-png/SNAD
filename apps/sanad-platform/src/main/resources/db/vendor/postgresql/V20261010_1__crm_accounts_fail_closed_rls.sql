-- Stage 30 P0: remove fail-open tenant context fallback for CRM accounts.
-- Deliberately limited to the confirmed vulnerable policy; not deployed until
-- application background and control-plane compatibility is reviewed.
-- PostgreSQL-only vendor migration; requires separate integration test gate.
ALTER POLICY tenant_isolation ON public.crm_accounts
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
