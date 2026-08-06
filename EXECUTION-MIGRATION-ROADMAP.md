# EXECUTION MIGRATION ROADMAP

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Version:** 1.0.0

---

## 1. Overview

This roadmap describes how the SANAD Execution Framework will evolve from its current inline location to a fully portable package.

---

## 2. Current State

| Aspect | Current |
|--------|---------|
| **Location** | `apps/web/lib/execution/` |
| **Structure** | Inline modules |
| **Import** | `@/lib/execution` |
| **Consumers** | CRM module only |
| **Build** | Next.js bundler |

---

## 3. Target State

| Aspect | Target |
|--------|--------|
| **Location** | `packages/execution/` |
| **Structure** | npm package |
| **Import** | `@sanad/execution` |
| **Consumers** | All modules |
| **Build** | Package manager (pnpm workspaces) |

---

## 4. Migration Phases

### Phase 1: Current (Complete)

**Status:** ✅ Done

**Location:** `apps/web/lib/execution/`

**What exists:**
- Core types
- Calculators
- Validators
- Provider interface
- React hooks
- Constants

**Consumer:** CRM module

---

### Phase 2: Multi-Module Adoption

**Status:** 🔄 In Progress

**Location:** `apps/web/lib/execution/`

**What to do:**
- Create providers for additional modules (ERP, POS, etc.)
- Migrate CRM to use the provider interface
- Validate cross-module consistency

**Consumer:** CRM + new modules

---

### Phase 3: Package Extraction

**Status:** ⏳ Pending

**Location:** `packages/execution/`

**What to do:**
1. Create `packages/execution/` directory
2. Add `package.json` with `@sanad/execution` name
3. Configure TypeScript for package mode
4. Move source files from `apps/web/lib/execution/`
5. Update imports in all consumers
6. Configure pnpm workspaces (or equivalent)

**Prerequisites:**
- Package manager configured
- Multiple modules consuming the framework

---

### Phase 4: Full Platform Integration

**Status:** ⏳ Pending

**Location:** `packages/execution/`

**What to do:**
- Backend API implementation
- Database schema for execution state
- CI/CD pipeline integration
- Cross-module governance

---

## 5. Migration Checklist

### Phase 2 → Phase 3

- [ ] Package manager configured (pnpm workspaces)
- [ ] `packages/execution/package.json` created
- [ ] `packages/execution/tsconfig.json` configured
- [ ] Source files moved
- [ ] Barrel export updated
- [ ] All consumers updated
- [ ] Tests passing
- [ ] Build passing
- [ ] Documentation updated

### Import Migration

```typescript
// Before (Phase 2)
import { calculateGroupProgress } from "@/lib/execution";

// After (Phase 3)
import { calculateGroupProgress } from "@sanad/execution";
```

---

## 6. Backward Compatibility

The migration MUST NOT break any public API:

| API | Before | After | Change |
|-----|--------|-------|--------|
| Types | Same | Same | None |
| Calculators | Same | Same | None |
| Validators | Same | Same | None |
| Providers | Same | Same | None |
| Hooks | Same | Same | None |
| Constants | Same | Same | None |

---

## 7. Rollback Plan

If the package extraction fails:

1. Revert source files to `apps/web/lib/execution/`
2. Revert import paths to `@/lib/execution`
3. Remove `packages/execution/`
4. Document failure原因

---

## 8. Timeline

| Phase | Target Date | Dependencies |
|-------|-------------|--------------|
| Phase 1 | 2026-08-03 | None |
| Phase 2 | 2026-08-10 | Module providers |
| Phase 3 | 2026-08-24 | Package manager |
| Phase 4 | 2026-09-07 | Backend API |

---

## 9. Success Criteria

- ✅ All modules use the execution framework
- ✅ No duplicated execution logic
- ✅ Package is portable across projects
- ✅ No public API changes during migration
- ✅ All tests pass at each phase
