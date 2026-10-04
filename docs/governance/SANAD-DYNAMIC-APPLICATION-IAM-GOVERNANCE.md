# SANAD Dynamic Application IAM Governance

> Status: APPROVED GOVERNANCE PATH
> Scope: Core platform application onboarding, Users / IAM discovery, RBAC/capability/scope integration
> Authority: This document becomes canonical when merged to protected `main`.
> Baseline: `325d2d0c2eac5077c6a6808506b6983243c0e3f2`

## 1. Decision

SANAD adopts a dynamic application IAM model.

```text
SANAD_DYNAMIC_APPLICATION_IAM = REQUIRED
APPLICATION_ONBOARDING_MODEL = CONTRACT_REGISTERED + AUTO_DISCOVERED
NEW_APPLICATION_REQUIRES_USERS_CODE_CHANGE = FALSE
USERS_MODULE_APP_HARDCODING = PROHIBITED
APPLICATION_REGISTRY = DISCOVERY_AND_METADATA_ONLY
AUTHORIZATION_ENGINE = AUTHORITATIVE
APPLICATION_ACCESS_SOURCE = ROLE + CAPABILITY + SCOPE
UNKNOWN_APPLICATION = FAIL_CLOSED
UNKNOWN_CAPABILITY = FAIL_CLOSED
UNKNOWN_SCOPE = FAIL_CLOSED
CROSS_TENANT_APPLICATION_GRANT = PROHIBITED
APPLICATION_IAM_VERSIONING = GOVERNED
POSTGRESQL_DIRECT_CERTIFICATION = REQUIRED
```

## 2. Architectural contract

Every current or future SANAD application must onboard to the platform through a versioned IAM Contract / Manifest.

The contract declares, at minimum:

- stable application code and version;
- localized display metadata;
- owned capability namespace(s);
- governed role templates when supplied;
- supported scope contracts;
- lifecycle/status metadata;
- compatibility metadata required by the platform registry.

Applications are not added to User Administration by editing Users source code.

The required flow is:

```text
Application
-> IAM Contract / Manifest
-> Registry validation
-> Application Registry
-> Canonical Authorization Engine
-> User Administration auto-discovery
-> Effective Access projection
```

## 3. Authority boundary

The Application Registry is a catalog and discovery layer only.

It may provide:

- application identity;
- display metadata;
- namespace ownership;
- supported scope metadata;
- IAM contract/version metadata;
- lifecycle information.

It must not:

- grant a role;
- grant a capability;
- bypass RBAC;
- infer authorization from an `enabled` flag;
- authorize cross-tenant access;
- create a second permission model.

All allow/deny decisions remain backend-authoritative through the canonical authorization engine.

## 4. Users Module invariants

The following are prohibited inside Users/User Administration:

- hardcoded application lists;
- application-name `if/else` authorization branches;
- browser-side booleans treated as permission authority;
- per-application duplicate user identities;
- per-application IAM stores;
- a new Users-side adapter for every application.

A conforming new application must become visible to User Administration without a Users Module code change.

## 5. Dynamic discovery behavior

User Administration reads the registry and effective-access projection dynamically.

For each registered application it may show:

- application name and lifecycle state;
- assigned roles;
- effective capabilities;
- supported/current scope;
- effective access;
- authorization source/reason where available.

Unknown, malformed, disabled, stale, or incompatible registrations fail closed.

## 6. Scope governance

The platform may support tenant, organization, and application/domain-specific scopes.

Application-specific scopes must be declared through the registered scope contract and enforced by a canonical backend authorization relationship/scope service.

User Administration consumes the generic scope contract. It must not add a new hardcoded scope implementation for each application.

## 7. Tenant isolation

Application registration never weakens tenant isolation.

Every user, role, grant, capability evaluation, override, relationship, and scope mutation remains bound to the authenticated tenant or trusted control-plane context.

Cross-tenant application grants are prohibited and must be covered by PostgreSQL Direct/security tests.

## 8. Versioning and compatibility

IAM contracts are versioned.

A breaking contract change requires governed migration/compatibility handling. User Administration must fail closed when a registry entry requires an unsupported contract version.

Silent reinterpretation of a capability namespace, scope contract, or role template is prohibited.

## 9. Acceptance gates

Dynamic Application IAM is not considered implemented until all of the following pass:

1. synthetic application registration with no application name referenced in Users source;
2. automatic discovery by User Administration;
3. role/capability/scope projection from canonical backend state;
4. effective access changes after valid grant/revoke;
5. unknown application/capability/scope fails closed;
6. cross-tenant mutation/read denial;
7. no hardcoded application list in Users;
8. no second authorization model;
9. PostgreSQL Direct security/regression suite GREEN;
10. authenticated E2E GREEN;
11. exact-head CI and independent review GREEN;
12. protected merge and post-merge verification GREEN.

## 10. Governance impact

This decision supersedes any earlier design language that treats CRM, HRM, Workflow, ERP, Finance, Ecommerce, POS, Executive, or any other application as hardcoded Users Module categories.

Those names may exist as registry data and product metadata, but not as architectural dependencies inside Users.

Any future application that cannot conform to this contract requires an explicit architecture/governance exception before integration. It must not be accommodated by silently editing Users.

## Final decision

```text
DYNAMIC_APPLICATION_IAM_GOVERNANCE = APPROVED
CORE_GOVERNANCE_PATH = ACTIVE_AFTER_MERGE
NEW_APPLICATION_REQUIRES_USERS_CODE_CHANGE = FALSE
APPLICATION_REGISTRY_AUTHORITY = DISCOVERY_ONLY
CANONICAL_AUTHORIZATION_ENGINE = SOLE_SECURITY_AUTHORITY
FAIL_CLOSED = REQUIRED
POSTGRESQL_DIRECT = REQUIRED
```
