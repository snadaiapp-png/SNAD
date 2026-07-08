# SANAD Production GO Checklist

Status: NO-GO until all sections are complete with exact-SHA evidence.

## Release state model

Hosting READY is not Application PASS.
Application PASS is not Governance GO.

| State | Meaning | Required evidence |
|---|---|---|
| HOSTING_READY | Vercel/hosting deployment completed | Deployment ID, commit SHA, deployment state |
| APPLICATION_PASS | Application path passed validation | Production smoke, BFF/backend reachability, auth/session, tenant isolation, rollback proof |
| GOVERNANCE_GO | Authorized production/commercial release | All blockers closed, distinct approvals, security evidence, release decision |

## Mandatory GO gates

- [ ] Issue #200: Production BFF/backend path validated end-to-end.
- [ ] Issue #197: AWS/OIDC live evidence achieved.
- [ ] Issue #292: Final security assessment closed.
- [ ] Issues #293-#298: TD-07 blockers closed.
- [ ] Issues #325-#330: Stage 09/10 child gates closed.
- [ ] Issue #331: Integrated final acceptance closed.
- [ ] Issue #324: Controller closed only after child gates.
- [ ] Issues #280-#291: Stage 08 closure packages complete.
- [ ] Production smoke test passes with zero failures.
- [ ] No Critical finding remains open.
- [ ] No High finding remains unaccepted.
- [ ] Rollback evidence attached.
- [ ] Five distinct approvals recorded.

## Approval evidence template

| Role | Account | Decision | Release SHA | UTC timestamp | Residual risk accepted | Evidence link |
|---|---|---|---|---|---|---|
| Security Owner | TBD | TBD | TBD | TBD | TBD | TBD |
| Infrastructure Owner | TBD | TBD | TBD | TBD | TBD | TBD |
| QA & Release Owner | TBD | TBD | TBD | TBD | TBD | TBD |
| System Owner | TBD | TBD | TBD | TBD | TBD | TBD |
| Project Manager | TBD | TBD | TBD | TBD | TBD | TBD |

## Final decision

Final decision is NO-GO until this file is completed by evidence, not by declaration.
