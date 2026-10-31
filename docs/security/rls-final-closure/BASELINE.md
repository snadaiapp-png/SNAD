# BASELINE — P0 RLS closure (batch 2)
- Base SHA: 5e854ef4426d7ff500675be69f15ff3358db917a (main, PR #1460 "fail-closed RLS for 92 tenant-owned tables + FORCE-bind 60 owner-bypass tables")
- Catalog at baseline (isolated PG, full 245-migration chain as role sanad): 289 tables RLS-enabled; six T2 tables VERIFIED rls=false/force=false/0 policies (matches tests/security/rls_tenant_owned_classification.json T2-DEFERRED-CONTRACT=6).
- G1 certification status: HOLD for 5e854ef (last SUCCESS bound to 1d80c573 / 2026-10-19).
- Production: Render sanad-backend last live deploy 2026-10-28T00:09:18Z (does not include 5e854ef).
