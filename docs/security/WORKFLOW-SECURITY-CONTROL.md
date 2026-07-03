# Workflow Security Control

**Status:** ACTIVE
**Date:** 2026-06-25

---

## Overview

The workflow security scanner (`scripts/ci/check_workflow_security.py`) is a structural YAML + shell content scanner that prevents unsafe credential-management workflows from being merged to `main`.

## How It Works

1. **Structural YAML parsing**: Uses PyYAML to parse workflow files
2. **Pattern detection**: Checks for 12+ prohibited patterns
3. **CI enforcement**: Runs as part of Security Baseline workflow
4. **Test coverage**: 15 deterministic test scenarios with 6 fixture files

## Prohibited Patterns

| Pattern | Type |
|---------|------|
| Password-like workflow_dispatch inputs | `password_dispatch_input` |
| Direct UPDATE users SET password_hash | `direct_password_hash_mutation` |
| Direct DELETE FROM refresh_tokens | `direct_refresh_token_deletion` |
| psycopg2 with Production environment | `production_psycopg2_access` |
| Render API + credential mutation | `render_env_credential_mutation` |
| Production user enumeration | `production_user_enumeration` |
| Printing user_id + tenant_id | `identity_logging` |
| Unpinned pip install with Production secrets | `unpinned_packages_with_secrets` |
| write-all permissions | `write_all_permissions` |
| Force-push (not --force-with-lease) | `force_push_command` |
| Direct push to main | `direct_main_push` |

## CI Integration

- Scanner runs in `security-baseline.yml` → `workflow-security-policy` job
- Added to `security-gate-summary.needs` (required for gate summary)
- Scanner tests run before scanner execution
