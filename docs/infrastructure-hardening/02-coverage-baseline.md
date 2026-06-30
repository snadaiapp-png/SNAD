# SNAD Coverage Baseline

Branch: infra/02a-debt-closure | Date: 2026-06-30

## Backend Coverage

JaCoCo is NOT configured in pom.xml. No coverage report was generated.

### Plan for Stage 03

1. Add `jacoco-maven-plugin` to pom.xml (build plugin only, no production dependency)
2. Run `./mvnw -B -ntp clean test` to generate `target/site/jacoco/index.html`
3. Record line/branch/class/method coverage
4. Set baseline policy: no unexplained regression

## Frontend Coverage

Vitest coverage is NOT configured. No coverage report was generated.

### Plan for Stage 03

1. Add `@vitest/coverage-v8` to devDependencies
2. Add `coverage` script to package.json
3. Run `npm run test:coverage` to generate report
4. Record statements/branches/functions/lines coverage
5. Set baseline policy: no unexplained regression

## Current Evidence (Proxy Metrics)

| Metric | Backend | Frontend |
|---|---|---|
| Source files | 130 | 47 |
| Test files | 50 | 22 |
| File ratio | 38.5% | 46.8% |
| Tests executed | 434 | 238 |
| Tests passed | 434 | 238 |
| Tests failed | 0 | 0 |
| Tests skipped | 11 | 0 |

**Note:** Test-to-code LoC ratio is NOT coverage. These are proxy metrics only.

## Policy

```
No unexplained coverage regression.
New tenant-isolation code requires tests.
New authentication and authorization code requires tests.
New migration validation logic requires tests.
```

## Debt

CD-02-P2-001 remains OPEN. Will be closed in Stage 03 when JaCoCo and Vitest coverage are configured.
