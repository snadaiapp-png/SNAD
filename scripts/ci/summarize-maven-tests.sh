#!/usr/bin/env bash
# ============================================================
# SANAD — Maven Test Summary Script
# ============================================================
# Reads surefire and failsafe XML reports and produces a
# deterministic test count. Used by EXEC-PROMPT-010R2 to
# reconcile test count discrepancies.
#
# Usage:
#   cd apps/sanad-platform
#   mvn clean verify
#   ../../scripts/ci/summarize-maven-tests.sh
# ============================================================
set -euo pipefail

SUREFIRE_DIR="${1:-target/surefire-reports}"
FAILSAFE_DIR="${2:-target/failsafe-reports}"

if [ ! -d "$SUREFIRE_DIR" ]; then
  echo "ERROR: Surefire reports directory not found: $SUREFIRE_DIR" >&2
  exit 1
fi

echo "=== SANAD Maven Test Summary ==="
echo ""
echo "Surefire reports dir: $SUREFIRE_DIR"
echo "Failsafe reports dir: $FAILSAFE_DIR"
echo ""

python3 << 'PYEOF'
import xml.etree.ElementTree as ET
import glob
import os
import sys

surefire_dir = os.environ.get('SUREFIRE_DIR', 'target/surefire-reports')
failsafe_dir = os.environ.get('FAILSAFE_DIR', 'target/failsafe-reports')

def parse_reports(dir_path, label):
    if not os.path.isdir(dir_path):
        return {
            'label': label,
            'files': 0,
            'tests': 0,
            'failures': 0,
            'errors': 0,
            'skipped': 0,
            'classes': [],
            'skipped_methods': []
        }

    classes = []
    skipped_methods = []
    totals = {'files': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}

    for f in sorted(glob.glob(os.path.join(dir_path, 'TEST-*.xml'))):
        try:
            tree = ET.parse(f)
            root = tree.getroot()
            tests = int(root.attrib.get('tests', 0))
            failures = int(root.attrib.get('failures', 0))
            errors = int(root.attrib.get('errors', 0))
            skipped = int(root.attrib.get('skipped', 0))
            classname = root.attrib.get('name', os.path.basename(f))

            classes.append({
                'name': classname,
                'tests': tests,
                'failures': failures,
                'errors': errors,
                'skipped': skipped
            })

            totals['files'] += 1
            totals['tests'] += tests
            totals['failures'] += failures
            totals['errors'] += errors
            totals['skipped'] += skipped

            # Extract skipped test methods (if any)
            if skipped > 0:
                for tc in root.iter('testcase'):
                    skipped_elem = tc.find('skipped')
                    if skipped_elem is not None:
                        skipped_methods.append({
                            'class': tc.attrib.get('classname', classname),
                            'method': tc.attrib.get('name', '?'),
                            'label': label
                        })
        except Exception as e:
            print(f"WARN: Could not parse {f}: {e}", file=sys.stderr)

    return {
        'label': label,
        'files': totals['files'],
        'tests': totals['tests'],
        'failures': totals['failures'],
        'errors': totals['errors'],
        'skipped': totals['skipped'],
        'classes': classes,
        'skipped_methods': skipped_methods
    }

# Set env vars for the python script
import os
os.environ['SUREFIRE_DIR'] = surefire_dir
os.environ['FAILSAFE_DIR'] = failsafe_dir

surefire = parse_reports(surefire_dir, 'Surefire (unit tests)')
failsafe = parse_reports(failsafe_dir, 'Failsafe (integration tests)')

print(f"--- {surefire['label']} ---")
print(f"  Test files: {surefire['files']}")
print(f"  Tests: {surefire['tests']}")
print(f"  Failures: {surefire['failures']}")
print(f"  Errors: {surefire['errors']}")
print(f"  Skipped: {surefire['skipped']}")
print()

print(f"--- {failsafe['label']} ---")
print(f"  Test files: {failsafe['files']}")
print(f"  Tests: {failsafe['tests']}")
print(f"  Failures: {failsafe['failures']}")
print(f"  Errors: {failsafe['errors']}")
print(f"  Skipped: {failsafe['skipped']}")
print()

total_tests = surefire['tests'] + failsafe['tests']
total_failures = surefire['failures'] + failsafe['failures']
total_errors = surefire['errors'] + failsafe['errors']
total_skipped = surefire['skipped'] + failsafe['skipped']

print("=== TOTALS ===")
print(f"  Total test files: {surefire['files'] + failsafe['files']}")
print(f"  Total tests: {total_tests}")
print(f"  Total failures: {total_failures}")
print(f"  Total errors: {total_errors}")
print(f"  Total skipped: {total_skipped}")
print()

# List skipped tests
all_skipped = surefire['skipped_methods'] + failsafe['skipped_methods']
if all_skipped:
    print("=== SKIPPED TEST METHODS ===")
    for s in all_skipped:
        print(f"  [{s['label']}] {s['class']}.{s['method']}")
    print()

# List all test classes
print("=== ALL TEST CLASSES ===")
for c in surefire['classes']:
    status = "✅" if c['failures'] == 0 and c['errors'] == 0 else "❌"
    skip_note = f" (skipped: {c['skipped']})" if c['skipped'] > 0 else ""
    print(f"  {status} {c['name']}: tests={c['tests']}{skip_note}")
print()

# Final verdict
if total_failures > 0 or total_errors > 0:
    print(f"❌ VERDICT: FAILURES={total_failures} ERRORS={total_errors}")
    sys.exit(1)
else:
    print(f"✅ VERDICT: ALL TESTS PASSED (total={total_tests}, skipped={total_skipped})")
PYEOF
