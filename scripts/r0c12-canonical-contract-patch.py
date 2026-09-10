from pathlib import Path

CI = Path('.github/workflows/ci.yml')
text = CI.read_text()

replacements = [
    (
        '          ALLOWED = {"CommerceOrderPostgresConcurrencyTest": 6, "RbacAccessCheckPostgresAcceptanceTest": 15}',
        '          ALLOWED = {"CommerceOrderPostgresConcurrencyTest": 6, "RbacAccessCheckPostgresAcceptanceTest": 15, "ModuleRegistryUatPostgresAcceptanceTest": 10}',
    ),
    (
        "            -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest' \\",
        "            -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest' \\",
    ),
    (
        '      - name: A6-17 Acceptance verdict (21 executed, 0F/0E/0S, 6+15)',
        '      - name: A6-17 Acceptance verdict (31 executed, 0F/0E/0S, 6+15+10)',
    ),
    (
        '          rbac = per_class.get("RbacAccessCheckPostgresAcceptanceTest", [0, 0, 0, 0])\n          ok = (tests == 21 and failures == 0 and errors == 0 and skipped == 0\n                and com[0] == 6 and rbac[0] == 15 and acc_exit == "0" and parse_errors == 0)',
        '          rbac = per_class.get("RbacAccessCheckPostgresAcceptanceTest", [0, 0, 0, 0])\n          module_uat = per_class.get("ModuleRegistryUatPostgresAcceptanceTest", [0, 0, 0, 0])\n          ok = (tests == 31 and failures == 0 and errors == 0 and skipped == 0\n                and com[0] == 6 and rbac[0] == 15 and module_uat[0] == 10\n                and acc_exit == "0" and parse_errors == 0)',
    ),
    (
        '              w.write("EXPECTED_ACCEPTANCE_TESTS=21\\n")',
        '              w.write("EXPECTED_ACCEPTANCE_TESTS=31\\n")',
    ),
    (
        '              w.write("RBAC_EXECUTED=%d\\n" % rbac[0])',
        '              w.write("RBAC_EXECUTED=%d\\n" % rbac[0])\n              w.write("MODULE_REGISTRY_UAT_EXECUTED=%d\\n" % module_uat[0])',
    ),
    (
        '              "PG_ACCEPTANCE==21/0/0/0": (acc.get("PG_ACCEPTANCE_TESTS") == "21"',
        '              "PG_ACCEPTANCE==31/0/0/0": (acc.get("PG_ACCEPTANCE_TESTS") == "31"',
    ),
    (
        '#     acceptance run (CommerceOrderPostgresConcurrencyTest = 6,\n#     RbacAccessCheckPostgresAcceptanceTest = 15); any unknown',
        '#     acceptance run (CommerceOrderPostgresConcurrencyTest = 6,\n#     RbacAccessCheckPostgresAcceptanceTest = 15,\n#     ModuleRegistryUatPostgresAcceptanceTest = 10); any unknown',
    ),
    (
        '#     pg-acceptance, BOTH gated classes in one explicit\n#     invocation, EXPECTED_ACCEPTANCE_TESTS=21 (Commerce=6,\n#     RBAC=15), 0F/0E/0S',
        '#     pg-acceptance, ALL THREE gated classes in one explicit\n#     invocation, EXPECTED_ACCEPTANCE_TESTS=31 (Commerce=6,\n#     RBAC=15, ModuleRegistryHumanUAT=10), 0F/0E/0S',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'FAIL_CLOSED: replacement count {count}, expected 1: {old[:120]!r}')
    text = text.replace(old, new, 1)

required = [
    '"ModuleRegistryUatPostgresAcceptanceTest": 10',
    "-Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'",
    'EXPECTED_ACCEPTANCE_TESTS=31',
    'MODULE_REGISTRY_UAT_EXECUTED=%d',
    'PG_ACCEPTANCE==31/0/0/0',
]
for marker in required:
    if marker not in text:
        raise SystemExit(f'FAIL_CLOSED: required marker absent: {marker}')

for forbidden in [
    'PG_ACCEPTANCE==21/0/0/0',
    'EXPECTED_ACCEPTANCE_TESTS=21',
    "-Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest' \\",
]:
    if forbidden in text:
        raise SystemExit(f'FAIL_CLOSED: stale canonical marker remains: {forbidden}')

CI.write_text(text)
print('G11_CANONICAL_CONTRACT_PATCH=APPLIED')
