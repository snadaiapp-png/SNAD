# SNAD API Compatibility Policy

## Contract Baseline
- OpenAPI spec stored at `docs/api-contracts/openapi-v1-baseline.yaml`
- Regenerated from running application via SpringDoc

## Compatibility Gate
- CI job `api-contract` compares generated spec against baseline
- Breaking changes fail CI
- Non-breaking additions pass CI

## Breaking Change Detection
1. Path removal → FAIL
2. Response field removal → FAIL
3. Type change → FAIL
4. Required request field addition → FAIL
5. Enum value removal → FAIL
6. Optional field addition → PASS
7. New endpoint → PASS
