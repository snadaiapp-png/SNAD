# HRM G4-T11 — Full certification (candidate)

Status: **IN PROGRESS — not certified; no protected merge yet**.

Base authority: G4-T10 merge `c43bd6e11c590ff49b7547de174e9024a1531b78`.
Closed predecessors (not reopened):
- T8: `db4a220950e0d7c682263534c4f1e224c13622b8`
- T9: `e6511a7bc1496086fa0f1fbc2c47383f516f7c2f`
- T10: `c43bd6e11c590ff49b7547de174e9024a1531b78`

## Required release criteria (all on T11 exact PR head)

- [ ] G4 focused backend and API contract tests
- [ ] Flyway validation and PostgreSQL Direct acceptance
- [ ] HRM security / RLS tenant isolation and capability denial
- [ ] Web CI / focused Vitest and TypeScript
- [ ] Full Maven/CI
- [ ] Security and governance checks
- [ ] HRM human preview where applicable
- [ ] Previous T8/T9/T10 post-merge checks independently verified by CI
- [ ] Valid independent approval on final exact SHA
- [ ] Protected squash merge only after terminal-green

## Evidence policy

Do not infer PASS from queued or in-progress jobs. No Docker/Testcontainers.
Do not claim WPS/GOSI/tax/bank or other statutory conformance.
Accounting alone owns journal and GL posting.
This certificate is a candidate until live check results and review are inspected.

G4-T12 post-merge certification is separate and must occur on the eventual T11 merge SHA.
