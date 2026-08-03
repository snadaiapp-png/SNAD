# FRAMEWORK SUPPORT POLICY — SANAD Execution Framework v1.0.0

**Purpose:** Define support levels, timelines, and processes for the SANAD Execution Framework.

---

## Support Levels

### Level 1: Active Support

**Duration:** Current release + 2 minor versions (approximately 6-12 months)

**Services:**
- Bug fixes
- Security patches
- Documentation updates
- Performance improvements
- New features (minor versions)

**Response Time:**
- Critical bugs: < 24 hours
- High bugs: < 48 hours
- Medium bugs: < 1 week
- Low bugs: < 2 weeks

### Level 2: Extended Support

**Duration:** After Active Support + 4 minor versions (approximately 12-24 months)

**Services:**
- Security patches
- Critical bug fixes
- Documentation updates

**Response Time:**
- Critical bugs: < 48 hours
- High bugs: < 1 week
- Medium bugs: < 2 weeks
- Low bugs: Best effort

### Level 3: Maintenance Support

**Duration:** After Extended Support + 2 major versions (approximately 24-48 months)

**Services:**
- Security patches only
- Critical bug fixes only

**Response Time:**
- Critical bugs: < 1 week
- High bugs: Best effort
- Medium bugs: Best effort
- Low bugs: Not supported

### Level 4: End of Life

**Duration:** After Maintenance Support

**Services:**
- No updates
- No patches
- No support

**Migration:** Must migrate to supported version

---

## Version Support Matrix

| Version | Status | Active Support | Extended Support | Maintenance Support | End of Life |
|---------|--------|----------------|------------------|---------------------|-------------|
| 1.0.x | Active | 2026-08-03 | 2027-02-03 | 2028-02-03 | 2030-02-03 |
| 1.1.x | Planned | 2026-11-03 | 2027-05-03 | 2028-05-03 | 2030-05-03 |
| 2.0.x | Planned | 2027-02-03 | 2027-08-03 | 2028-08-03 | 2030-08-03 |

---

## Bug Severity Levels

### Critical (P0)

**Definition:**
- Framework is unusable
- Data loss or corruption
- Security vulnerability
- Production down

**Response:** < 24 hours
**Resolution:** < 48 hours
**Escalation:** Immediate

### High (P1)

**Definition:**
- Major feature broken
- Performance degradation > 50%
- Workaround not available
- Affects multiple modules

**Response:** < 48 hours
**Resolution:** < 1 week
**Escalation:** After 48 hours

### Medium (P2)

**Definition:**
- Minor feature broken
- Performance degradation < 50%
- Workaround available
- Affects single module

**Response:** < 1 week
**Resolution:** < 2 weeks
**Escalation:** After 1 week

### Low (P3)

**Definition:**
- Cosmetic issue
- Minor inconvenience
- Easy workaround
- Documentation issue

**Response:** < 2 weeks
**Resolution:** < 1 month
**Escalation:** After 2 weeks

---

## Support Channels

### GitHub Issues

**URL:** https://github.com/snadaiapp-png/SNAD/issues

**Use for:**
- Bug reports
- Feature requests
- Documentation issues
- General questions

**Response Time:** Varies by severity

### Documentation

**Files:**
- `FRAMEWORK-DEVELOPER-GUIDE.md` — Development guide
- `EXECUTION-FRAMEWORK-API.md` — API reference
- `FRAMEWORK-RELEASE-NOTES.md` — Release notes
- `FRAMEWORK-UPGRADE-GUIDE.md` — Upgrade guide
- `FRAMEWORK-MAINTENANCE-GUIDE.md` — Maintenance guide
- `FRAMEWORK-SUPPORT-POLICY.md` — This document

### Email

**Address:** (TBD)

**Use for:**
- Security vulnerabilities
- Private bug reports
- Escalations

**Response Time:** < 24 hours for critical issues

---

## Deprecation Policy

### Timeline

1. **Announcement:** Deprecated in minor version N
2. **Warning:** Warning in minor versions N+1, N+2, N+3
3. **Removal:** Removed in major version N+4

### Example

- **v1.1.0:** Function `oldFunction` deprecated
- **v1.2.0:** Warning added to `oldFunction`
- **v1.3.0:** Warning remains
- **v1.4.0:** Warning remains
- **v2.0.0:** `oldFunction` removed

### Communication

1. **Release Notes:** Deprecation announced in release notes
2. **Documentation:** Deprecation notice in documentation
3. **Console Warning:** Runtime warning when deprecated function used
4. **Migration Guide:** Instructions for migrating to new function

---

## Breaking Changes

### Definition

A breaking change is any change that:
- Removes a public API
- Changes function signature
- Changes return type
- Changes behavior
- Requires code changes

### Policy

1. **Stable APIs:** No breaking changes without major version bump
2. **Internal APIs:** Can change at any time
3. **Deprecation:** 4-version deprecation period
4. **Migration:** Migration guide provided

### Process

1. **Proposal:** ADR (Architecture Decision Record)
2. **Review:** Team review and approval
3. **Implementation:** Implementation with tests
4. **Documentation:** Migration guide and updated docs
5. **Communication:** Announced in release notes

---

## Security Policy

### Reporting

1. **Private Disclosure:** Report privately to security@snad.ai
2. **GitHub Issues:** Use GitHub security advisory
3. **Response:** Acknowledge within 24 hours

### Patching

1. **Critical:** Patch within 48 hours
2. **High:** Patch within 1 week
3. **Medium:** Patch within 2 weeks
4. **Low:** Patch in next release

### Updates

1. **Security Updates:** Released as PATCH versions
2. **Notifications:** Announced in release notes
3. **Documentation:** Updated security advisories

---

## Feature Requests

### Process

1. **Submit:** GitHub Issue with `feature-request` label
2. **Review:** Team reviews within 1 week
3. **Discussion:** Public discussion
4. **Decision:** Accept or decline
5. **Implementation:** If accepted, implement in next minor version

### Criteria

- **Alignment:** Must align with framework goals
- **Value:** Must provide value to multiple modules
- **Feasibility:** Must be technically feasible
- **Maintenance:** Must not increase maintenance burden significantly

---

## Module Support

### Adopted Modules

| Module | Status | Support Level |
|--------|--------|---------------|
| CRM | Adopted | Active Support |

### Ready Modules

| Module | Status | Support Level |
|--------|--------|---------------|
| PM | Ready | Not Adopted |
| HR | Ready | Not Adopted |
| Finance | Ready | Not Adopted |
| Operations | Ready | Not Adopted |

### Planned Modules

| Module | Status | Support Level |
|--------|--------|---------------|
| IT | Planned | Not Supported |
| Legal | Planned | Not Supported |
| Marketing | Planned | Not Supported |
| Sales | Planned | Not Supported |
| Support | Planned | Not Supported |
| R&D | Planned | Not Supported |
| Quality | Planned | Not Supported |
| Procurement | Planned | Not Supported |
| Logistics | Planned | Not Supported |
| Training | Planned | Not Supported |

---

## Governance

### Rules

1. **Single Source of Truth:** All execution data from providers
2. **No Duplicated Logic:** Use shared calculators, validators, constants
3. **Automated Validation:** Integrity checks in CI
4. **Change Management:** ADR for breaking changes
5. **Quality Gates:** All 7 gates must pass

### Enforcement

1. **Contract Tests:** Enforce provider compliance
2. **Integrity Validation:** Enforce governance rules
3. **Code Review:** Enforce best practices
4. **CI/CD:** Automated enforcement

---

## Escalation

### Levels

1. **Level 1:** Module maintainer
2. **Level 2:** Framework maintainer
3. **Level 3:** Platform team
4. **Level 4:** Leadership

### Process

1. **Document:** Document the issue
2. **Notify:** Notify the next level
3. **Provide:** Provide diagnosis and proposed fix
4. **Monitor:** Monitor resolution
5. **Post-mortem:** Post-mortem if needed

---

## Communication

### Channels

- **GitHub Issues:** Public discussion
- **Release Notes:** Version updates
- **Documentation:** Guides and references
- **Email:** Private communication

### Frequency

- **Weekly:** Review open issues
- **Monthly:** Release notes
- **Quarterly:** Major updates
- **Annually:** Support level review

---

## Responsibilities

### Framework Maintainer

- Maintain framework code
- Review and merge PRs
- Release new versions
- Update documentation
- Respond to issues

### Module Maintainer

- Adopt framework in module
- Implement provider
- Write tests
- Report bugs
- Request features

### Platform Team

- Provide infrastructure
- Enforce governance
- Escalate critical issues
- Strategic decisions

---

## Metrics

### Track

- Issue response time
- Issue resolution time
- Bug severity distribution
- Module adoption rate
- Test coverage
- Build success rate

### Review

- **Weekly:** Issue metrics
- **Monthly:** Release metrics
- **Quarterly:** Support metrics
- **Annually:** Overall health

---

## Resources

- **Documentation:** `FRAMEWORK-DEVELOPER-GUIDE.md`
- **API Reference:** `EXECUTION-FRAMEWORK-API.md`
- **Release Notes:** `FRAMEWORK-RELEASE-NOTES.md`
- **Upgrade Guide:** `FRAMEWORK-UPGRADE-GUIDE.md`
- **Maintenance Guide:** `FRAMEWORK-MAINTENANCE-GUIDE.md`

---

**Last Updated:** 2026-08-03
**Framework Version:** 1.0.0
**Policy Version:** 1.0.0
