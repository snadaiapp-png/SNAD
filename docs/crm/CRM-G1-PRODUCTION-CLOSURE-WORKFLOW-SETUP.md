# CRM-G1 Production Closure — Quick Setup

## 1. Add the workflow file

Upload `crm-g1-production-closure.yml` to this repository path:

```text
.github/workflows/crm-g1-production-closure.yml
```

Commit it through a pull request and merge it into `main`.

## 2. Configure the Production environment

In GitHub:

```text
Settings → Environments → Production
```

Enable required reviewers before deployment.

## 3. Add the three database secrets

Inside the `Production` environment, add:

```text
PROD_JDBC_URL
PROD_DB_USER
PROD_DB_PASSWORD
```

`PROD_JDBC_URL` must start with `jdbc:postgresql://` and must not contain the password.

The workflow reuses the existing Vercel and controlled tenant secrets when present:

```text
VERCEL_TOKEN
VERCEL_PROJECT_ID
VERCEL_TEAM_ID
AUTH_SMOKE_TENANT_A_EMAIL
AUTH_SMOKE_TENANT_A_PASSWORD
AUTH_SMOKE_TENANT_B_EMAIL
AUTH_SMOKE_TENANT_B_PASSWORD
```

Optional CRM-specific account overrides:

```text
CRM_TENANT_A_EMAIL
CRM_TENANT_A_PASSWORD
CRM_TENANT_B_EMAIL
CRM_TENANT_B_PASSWORD
```

## 4. Run the workflow

Open:

```text
Actions → CRM G1 Production Closure → Run workflow
```

Enter only:

```text
backup_reference
change_approval_reference
```

Both values must be real, traceable references—not placeholders.

## 5. Finalize closure

When all checks pass, the workflow:

- uploads a 90-day immutable evidence artifact;
- writes the CRM-G1 production evidence record;
- opens an evidence pull request.

Review and merge that evidence pull request to finalize CRM-G1.

`REM-P0-006` remains an independent security-assurance gate and is not closed by this workflow.
