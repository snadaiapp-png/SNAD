#!/usr/bin/env python3
"""Static regression guard: no executable raw app.tenant_id setters in production Java.

Only explicitly profiled, non-production fixture bootstraps are exempt.
This is a source-inventory gate, not a replacement for Maven/PostgreSQL RLS tests.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / 'src/main/java'
ALLOW = {
    'com/sanad/platform/security/config/PerfTestBootstrapConfig.java': 'perf-test',
    'com/sanad/platform/security/config/CrmAcceptanceBootstrapConfig.java': 'crm-acceptance',
    'com/sanad/platform/workflow/config/WorkflowE2eBootstrapConfig.java': 'workflow-e2e',
}
PATTERN = re.compile(r"set_config\s*\(\s*['\"]app\.tenant_id", re.I)
violations = []
fixtures = 0
scanned = 0
for path in sorted(ROOT.rglob('*.java')):
    scanned += 1
    rel = path.relative_to(ROOT).as_posix()
    content = path.read_text(encoding='utf-8')
    for number, line in enumerate(content.splitlines(), 1):
        if not PATTERN.search(line) or line.lstrip().startswith(('//', '*')):
            continue
        allowed_profile = ALLOW.get(rel)
        if allowed_profile and re.search(r'@Profile\s*\(\s*"' + re.escape(allowed_profile) + r'"\s*\)', content):
            fixtures += 1
        else:
            violations.append(f'{rel}:{number}: {line.strip()}')
print(f'Java sources scanned: {scanned}; explicitly profiled fixture setters: {fixtures}; production raw tenant setters: {len(violations)}')
if violations:
    print('\n'.join(violations))
    sys.exit(1)
