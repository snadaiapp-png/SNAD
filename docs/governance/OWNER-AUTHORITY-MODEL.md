# SANAD Owner Authority Model

## Executive decision

The official project governance model is changed from multi-account independent approval to **Project Owner Sole Authority**.

The GitHub owner account `snadaiapp-png` is the single controlling authority for repository governance, merge authorization, release execution, and project-level decisions.

## Authority rule

| Decision area | Authorized approver |
|---|---|
| Repository governance | Project Owner account |
| Pull request merge | Project Owner account |
| Production release authorization | Project Owner account |
| Residual risk acceptance | Project Owner account |
| Emergency remediation | Project Owner account |
| Security incident closure | Project Owner account |

## Controls that remain mandatory

Owner-only approval does not remove technical controls. The following remain required:

- CI must pass.
- Security baseline must pass.
- Production GO Governance Guard must pass.
- Release Blocker State Report must run.
- Production BFF/backend evidence must be attached before production GO.
- Critical findings must be closed before final release.
- High findings must be remediated or explicitly owner-accepted.
- Secrets must never be posted in issues, PRs, commits, logs, or chat.

## Superseded rule

The previous requirement for five distinct approval accounts is superseded by this owner authority model.

Issue #298 is converted from `five independent accounts required` to `owner authority confirmation required`.

## Final rule

A release can be approved by the Project Owner account only after the repository evidence gates pass. Owner authority replaces multi-account approval; it does not replace evidence.
