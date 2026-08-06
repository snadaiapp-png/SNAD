# FRAMEWORK MAINTENANCE GUIDE — SANAD Execution Framework v1.0.0

**Purpose:** Guide for maintaining and operating the SANAD Execution Framework.

---

## Overview

This guide covers ongoing maintenance tasks for the execution framework. It includes monitoring, updates, troubleshooting, and best practices.

---

## Monitoring

### Health Checks

Run daily:

```bash
# Full integrity validation
npx tsx scripts/validate-execution-integrity.ts

# Contract tests
npx vitest run lib/execution/contract-tests.test.ts

# TypeScript build
npx tsc --noEmit
```

### Dashboard Monitoring

Check the Execution Dashboard regularly:
- **URL:** `/control-plane/execution`
- **Metrics:** Module progress, certification status
- **Alerts:** Look for modules with progress < 0% or > 100%

### Key Metrics

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Contract Tests | 20/20 | < 20 |
| Integrity Rules | 28/28 | < 28 |
| TypeScript Build | 0 errors | > 0 |
| Production Build | Success | Failure |

---

## Routine Maintenance

### Weekly

1. **Review contract tests** — Ensure all 20 tests pass
2. **Check integrity validation** — Ensure all 28 rules pass
3. **Review module adoption** — Check for new modules adopting framework
4. **Review documentation** — Ensure docs are up to date

### Monthly

1. **Review API stability** — Check for any breaking changes
2. **Review deprecations** — Ensure deprecated items have migration paths
3. **Review test coverage** — Ensure coverage is adequate
4. **Review performance** — Check for performance regressions

### Quarterly

1. **Review versioning** — Consider minor/major version bump
2. **Review governance** — Ensure all governance rules are followed
3. **Review change management** — Ensure all changes follow process
4. **Review support** — Ensure support channels are working

---

## Updates

### Adding a New Calculator

1. Add function to `apps/web/lib/execution/calculators.ts`
2. Export from `apps/web/lib/execution/index.ts`
3. Add contract test to `apps/web/lib/execution/contract-tests.test.ts`
4. Update documentation
5. Run integrity validation
6. Commit with message: `feat(execution): add new calculator`

### Adding a New Validator

1. Add function to `apps/web/lib/execution/validators.ts`
2. Export from `apps/web/lib/execution/index.ts`
3. Add contract test to `apps/web/lib/execution/contract-tests.test.ts`
4. Update documentation
5. Run integrity validation
6. Commit with message: `feat(execution): add new validator`

### Adding a New Hook

1. Add function to `apps/web/lib/execution/hooks.ts`
2. Export from `apps/web/lib/execution/index.ts`
3. Add contract test to `apps/web/lib/execution/contract-tests.test.ts`
4. Update documentation
5. Run integrity validation
6. Commit with message: `feat(execution): add new hook`

### Modifying Existing Function

1. **Check API classification** — Is it Stable or Internal?
2. **If Stable:** Follow deprecation policy (4 versions)
3. **If Internal:** Can modify directly
4. Update contract tests
5. Update documentation
6. Run integrity validation
7. Commit with message: `fix(execution): fix function name`

---

## Troubleshooting

### Issue: Contract Tests Failing

**Symptoms:** Some contract tests fail

**Diagnosis:**
1. Check which test fails
2. Read the error message
3. Check the provider implementation

**Common Causes:**
- Provider method not implemented
- Wrong return type
- Wrong data format

**Fix:**
1. Implement missing method
2. Fix return type
3. Fix data format

### Issue: Integrity Validation Failing

**Symptoms:** Some integrity rules fail

**Diagnosis:**
1. Check which rule fails
2. Read the rule description
3. Check the data

**Common Causes:**
- Duplicated logic (calculator, validator, constant)
- Hardcoded progress
- Manual certification

**Fix:**
1. Remove duplicated logic
2. Use calculators for progress
3. Use validators for certification

### Issue: TypeScript Build Failing

**Symptoms:** `tsc --noEmit` returns errors

**Diagnosis:**
1. Read the error messages
2. Check the file causing the error

**Common Causes:**
- Type mismatch
- Missing import
- Wrong syntax

**Fix:**
1. Fix type mismatch
2. Add missing import
3. Fix syntax

### Issue: Production Build Failing

**Symptoms:** `npm run build` fails

**Diagnosis:**
1. Read the error messages
2. Check the component causing the error

**Common Causes:**
- Server component importing client-only code
- Missing export
- Wrong import path

**Fix:**
1. Move client code to separate file
2. Add missing export
3. Fix import path

---

## Performance

### Bundle Size

Monitor framework bundle size:
- Current: ~15KB gzipped
- Target: < 20KB gzipped
- Alert: > 25KB gzipped

### Render Performance

Monitor component render performance:
- Progress calculation: < 50ms
- Validation: < 100ms
- Dashboard render: < 200ms

### Optimization Tips

1. **Use memoization** — Use hooks that memoize calculations
2. **Avoid recomputation** — Don't calculate progress in render
3. **Lazy loading** — Lazy load dashboard components
4. **Code splitting** — Split framework code by module

---

## Security

### Dependency Updates

Monitor dependencies for security vulnerabilities:
- React: Check for CVEs
- TypeScript: Check for CVEs
- Next.js: Check for CVEs

### Code Review

Review all framework changes for:
- No hardcoded credentials
- No insecure imports
- No exposed secrets

### Access Control

Control access to framework:
- **Stable exports:** Public, documented
- **Internal exports:** Package-internal only
- **Validation functions:** Public, documented

---

## Best Practices

### Code Style

1. **Follow existing patterns** — Match existing code style
2. **Use TypeScript** — Strong typing for all functions
3. **Add comments** — Document complex logic
4. **Use constants** — Don't hardcode values

### Testing

1. **Test edge cases** — Test empty arrays, null values
2. **Test error cases** — Test invalid inputs
3. **Test integration** — Test with real data
4. **Test performance** — Test with large datasets

### Documentation

1. **Document new functions** — Add JSDoc comments
2. **Update README** — Keep README current
3. **Add examples** — Show usage examples
4. **Update upgrade guide** — Document breaking changes

---

## Escalation

### When to Escalate

- Contract tests failing for > 1 hour
- Integrity validation failing for > 1 hour
- Production build failing for > 1 hour
- Performance degradation > 50%

### Who to Escalate

1. **Module owner** — For module-specific issues
2. **Framework maintainer** — For framework-specific issues
3. **Platform team** — For platform-wide issues

### Escalation Process

1. Document the issue
2. Notify the responsible team
3. Provide diagnosis and proposed fix
4. Monitor resolution
5. Post-mortem if needed

---

## Resources

- **Documentation:** `FRAMEWORK-DEVELOPER-GUIDE.md`
- **API Reference:** `EXECUTION-FRAMEWORK-API.md`
- **Release Notes:** `FRAMEWORK-RELEASE-NOTES.md`
- **Upgrade Guide:** `FRAMEWORK-UPGRADE-GUIDE.md`

---

**Last Updated:** 2026-08-03
**Framework Version:** 1.0.0
