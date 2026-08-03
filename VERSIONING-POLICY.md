# VERSIONING POLICY — SANAD Execution Framework

**Status:** ADOPTED
**Version:** 1.0.0
**Date:** 2026-08-03

---

## Semantic Versioning

The SANAD Execution Framework adopts [Semantic Versioning 2.0.0](https://semver.org/).

### Version Format

```
MAJOR.MINOR.PATCH
```

| Component | Description |
|-----------|-------------|
| **MAJOR** | Incompatible API changes |
| **MINOR** | Backward-compatible functionality additions |
| **PATCH** | Backward-compatible bug fixes |

---

## Version Rules

### PATCH (0.0.x)

**When to use:**
- Bug fixes that don't change API
- Documentation updates
- Internal refactoring
- Test improvements
- Performance optimizations (no API change)

**Examples:**
```bash
# Bug fix
git tag execution-framework-v1.0.1

# Documentation update
git tag execution-framework-v1.0.2
```

**Requirements:**
- All tests must pass
- No API changes
- No breaking changes

---

### MINOR (0.x.0)

**When to use:**
- New functionality (backward-compatible)
- New exports (types, functions, constants)
- New optional parameters
- New hooks
- New validators
- New calculators

**Examples:**
```bash
# New validator added
git tag execution-framework-v1.1.0

# New hook added
git tag execution-framework-v1.2.0
```

**Requirements:**
- All tests must pass
- All existing functionality preserved
- New exports documented
- Migration guide provided (if needed)

---

### MAJOR (x.0.0)

**When to use:**
- Breaking API changes
- Removing exports
- Changing function signatures
- Changing type definitions
- Changing return types
- Adding required parameters

**Examples:**
```bash
# Breaking change
git tag execution-framework-v2.0.0
```

**Requirements:**
- All tests must pass
- Breaking changes documented
- Migration guide provided
- Deprecation period (if possible)

---

## Version Lifecycle

### Current Version

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Release Date** | 2026-08-03 |
| **Status** | Active |
| **End of Life** | TBD |

### Version Roadmap

| Version | Target Date | Features |
|---------|-------------|----------|
| 1.0.0 | 2026-08-03 | Initial release |
| 1.1.0 | Q4 2026 | ERP provider, Finance provider |
| 1.2.0 | Q1 2027 | Inventory provider, POS provider |
| 2.0.0 | Q2 2027 | Breaking changes (if needed) |

---

## Deprecation Policy

### Deprecation Process

1. **Announce** — Document deprecation in release notes
2. **Warn** — Add deprecation warnings to code
3. **Migrate** — Provide migration guide
4. **Remove** — Remove in next major version

### Deprecation Timeline

| Phase | Duration | Description |
|-------|----------|-------------|
| Announcement | 1 minor version | Deprecation announced |
| Warning | 2 minor versions | Warnings added |
| Migration | 1 minor version | Migration guide provided |
| Removal | Next major version | Deprecated API removed |

### Example

```
v1.0.0 — API announced as deprecated
v1.1.0 — Warnings added
v1.2.0 — Migration guide provided
v2.0.0 — Deprecated API removed
```

---

## Release Process

### Pre-Release Checklist

- [ ] All tests passing
- [ ] TypeScript build clean
- [ ] Contract tests passing
- [ ] Integrity validation passing
- [ ] Documentation updated
- [ ] Release notes generated
- [ ] Version bumped
- [ ] Git tag created

### Release Steps

```bash
# 1. Update version in package.json
npm version minor

# 2. Run tests
npx vitest run

# 3. Run integrity validation
npx tsx scripts/validate-execution-integrity.ts

# 4. Build
npm run build

# 5. Commit
git add -A
git commit -m "chore: release v1.1.0"

# 6. Tag
git tag -a execution-framework-v1.1.0 -m "Release v1.1.0"

# 7. Push
git push origin main --tags
```

---

## Version Compatibility

### Backward Compatibility

| Change Type | Compatibility | Version Bump |
|-------------|---------------|--------------|
| New export | ✅ Backward-compatible | Minor |
| Optional parameter | ✅ Backward-compatible | Minor |
| Bug fix | ✅ Backward-compatible | Patch |
| New required parameter | ❌ Breaking | Major |
| Removed export | ❌ Breaking | Major |
| Changed signature | ❌ Breaking | Major |

### Consumer Compatibility

| Consumer Version | Compatible With |
|------------------|-----------------|
| 1.0.x | 1.0.0+ |
| 1.x.x | 1.0.0+ |
| 2.0.x | 2.0.0+ |

---

## Version Documentation

### Release Notes

Every release MUST include:
- Version number
- Release date
- Changes (Added, Changed, Deprecated, Removed, Fixed, Security)
- Migration guide (if breaking changes)

### Changelog Format

```markdown
# Changelog

## [1.1.0] - 2026-10-01

### Added
- ERP ExecutionProvider
- Finance ExecutionProvider

### Changed
- None

### Deprecated
- None

### Removed
- None

### Fixed
- None

### Security
- None
```

---

## Certification

✅ Semantic Versioning adopted
✅ Version rules defined
✅ Deprecation policy defined
✅ Release process documented
✅ Compatibility matrix documented

**VERSION GOVERNANCE STATUS: ACTIVE**
