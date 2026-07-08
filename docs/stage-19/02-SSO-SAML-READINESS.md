# Stage 19 — SSO/SAML Implementation Readiness

**Date**: 2026-07-08
**Status**: DOCUMENTED

---

## SSO/SAML Objective

Enable enterprise customers to authenticate SNAD users via their existing
Identity Provider (IdP), eliminating the need for separate SNAD passwords
and enabling centralized user management.

## Enterprise Use Cases

```
1. Corporate Authentication
   - Employees use corporate credentials (e.g., Azure AD) to access SNAD
   - No separate SNAD password needed
   - IT manages access centrally

2. Automated Provisioning/Deprovisioning
   - When employee joins, IT grants SNAD access via IdP group
   - When employee leaves, IT revokes access → SNAD access removed

3. Multi-Factor Authentication (MFA)
   - Leverage IdP's MFA (Microsoft, Google, Okta)
   - SNAD inherits enterprise security policies

4. Compliance
   - SSO audit trail in IdP
   - Password policies enforced by IdP (not SNAD)
   - Session timeout controlled by IdP

5. Single Logout (SLO)
   - Logging out of corporate portal logs out of SNAD
   - Reduces security risk of abandoned sessions
```

## Target Identity Providers

### Microsoft Entra ID (Azure AD)

```
Protocol: SAML 2.0 / OIDC
Market share: Largest in enterprise (especially Saudi/GCC)
Integration: SP-initiated + IdP-initiated SSO
User provisioning: SCIM 2.0 (future)
MFA: Azure AD Conditional Access
Priority: HIGH (most enterprise customers use Microsoft)
```

### Google Workspace

```
Protocol: SAML 2.0 / OIDC
Market share: Common in tech-forward companies
Integration: SP-initiated SSO
User provisioning: Google Workspace API (future)
MFA: Google 2-Step Verification
Priority: MEDIUM
```

### Okta

```
Protocol: SAML 2.0 / OIDC
Market share: Common in US/EU enterprises
Integration: SP-initiated + IdP-initiated SSO
User provisioning: SCIM 2.0 (future)
MFA: Okta MFA
Priority: MEDIUM
```

### Auth0

```
Protocol: SAML 2.0 / OIDC
Market share: Common in developer-centric companies
Integration: SP-initiated SSO
User provisioning: Auth0 Management API (future)
MFA: Auth0 MFA
Priority: LOW
```

## Integration Requirements

### Technical Requirements

```
1. SAML 2.0 Service Provider (SP) implementation in Spring Boot
   Library: Spring Security SAML2 or Pac4j
   Entity ID: https://snad-app.vercel.app/saml/metadata
   ACS URL: https://snad-app.vercel.app/saml/SSO

2. Per-tenant IdP configuration
   - Each tenant configures their own IdP
   - IdP metadata stored per tenant (encrypted)
   - Tenant admin uploads IdP metadata or enters IdP URL

3. User mapping
   - SAML assertion → SNAD user
   - Email claim → user email (unique identifier)
   - Name claim → display name
   - Group/role claim → SNAD role mapping

4. Fallback authentication
   - Email/password still available (for non-SSO users)
   - Tenant admin can enforce SSO-only mode
   - Break-glass account for admin recovery
```

### Login Flow

```
1. User navigates to SNAD login page
2. User enters email (or selects tenant)
3. SNAD checks if tenant has SSO configured
4. If SSO: Redirect to IdP login page
5. User authenticates at IdP (with MFA if configured)
6. IdP sends SAML assertion to SNAD ACS URL
7. SNAD validates SAML assertion
8. SNAD maps assertion to user (by email)
9. If user exists: Create session, redirect to /workspace
10. If user doesn't exist: Auto-provision (if configured) or reject

For IdP-initiated:
1. User clicks SNAD app in IdP portal
2. IdP sends SAML assertion to SNAD ACS URL
3. SNAD validates and creates session
4. Redirect to /workspace
```

## User-Tenant Binding

```
SSO users are bound to tenants via:
1. Tenant IdP configuration: User authenticates via tenant's IdP → bound to that tenant
2. Email domain matching: User email domain matches tenant's configured domain
3. Manual assignment: Tenant admin invites user (for mixed SSO/non-SSO tenants)

Multi-tenant SSO:
- User may have SSO access to multiple tenants (if each tenant has IdP configured)
- TenantPicker shown when multiple SSO tenants are available
- Cross-tenant access still blocked (tenant isolation enforced)
```

## Permission Management

```
SSO does NOT change RBAC:
- Roles still managed by tenant admin (ADMIN, MANAGER, USER, VIEWER)
- SSO provides authentication (who you are)
- SNAD provides authorization (what you can do)

IdP group → SNAD role mapping (optional, future):
- IdP group "SNAD-Admins" → ADMIN role
- IdP group "SNAD-Users" → USER role
- Tenant admin configures mapping
- Changes in IdP groups sync to SNAD roles (via SCIM or periodic poll)
```

## Security Risks

```
1. SAML assertion forgery
   Mitigation: Validate assertion signature with IdP certificate
   Mitigation: Check assertion validity window (NotBefore/NotOnOrAfter)
   Mitigation: Prevent replay attacks (assertion ID cache)

2. IdP certificate rotation
   Mitigation: Monitor IdP metadata for certificate changes
   Mitigation: Alert tenant admin when certificate expires
   Mitigation: Support multiple signing certificates (rotation window)

3. Session hijacking
   Mitigation: HTTPS everywhere (TLS 1.2+)
   Mitigation: Short session lifetime (access token 15 min)
   Mitigation: Refresh token rotation
   Mitigation: HttpOnly, Secure, SameSite cookies

4. Account lockout
   Mitigation: Break-glass account (stored securely)
   Mitigation: Tenant admin can disable SSO enforcement
   Mitigation: Owner can reset tenant SSO config

5. Data exposure in SAML assertion
   Mitigation: SAML assertions contain minimal data (email, name)
   Mitigation: No sensitive data in assertions (no passwords, no financial data)
   Mitigation: Assertions are encrypted (if IdP supports)
```

## Future Technical Requirements

```
Phase 1 (Stage 20): SAML 2.0 SP implementation
  - Spring Security SAML2 integration
  - Per-tenant IdP metadata management
  - User mapping (email-based)
  - Fallback authentication (email/password)
  - Break-glass admin account

Phase 2 (Stage 21): SCIM 2.0 provisioning
  - Automated user creation from IdP
  - Automated user deactivation when removed from IdP
  - Group → role mapping

Phase 3 (Stage 22+): OIDC support
  - OpenID Connect as alternative to SAML
  - OAuth 2.0 for API access
  - Token-based authentication for mobile (future)
```

## Decision

```
SSO/SAML Readiness: DOCUMENTED
Design readiness: READY (architecture defined)
Implementation readiness: NOT YET (requires development)
Priority: HIGH (enterprise customers require SSO)
Target stage for implementation: Stage 20

Decision: Ready for design phase. Implementation deferred to Stage 20.
```
