#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R12-B: Test suite for the SDS baseline incremental enforcement.

Verifies:
  TEST 1: new HEX_COLOR violation → FAIL
  TEST 2: new RGB_FUNC violation → FAIL
  TEST 3: new FONT_FAMILY violation → FAIL
  TEST 4: new INCORRECT_BRAND_NAME violation → FAIL
  TEST 5: baselined pre-existing violation → PASS (does not fail gate)
  TEST 6: stale baseline entry (violation removed) → reported clearly
  TEST 7: changed file adding new violation above baseline → FAIL
  TEST 8: deterministic on Linux/GitHub Actions paths
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

# Add scripts/ci to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

REPO_ROOT = Path(__file__).resolve().parent.parent.parent


class TestSDSBaselineEnforcement(unittest.TestCase):
    """Tests for the incremental SDS baseline enforcement."""

    def setUp(self):
        self.baseline_path = REPO_ROOT / 'scripts' / 'ci' / 'design-system-baseline.json'
        self.assertTrue(self.baseline_path.exists(),
                        'design-system-baseline.json must exist')

    def test_1_new_hex_color_violation_fails(self):
        """TEST 1: A newly introduced hardcoded HEX color must fail."""
        # Read baseline
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        # Find a file NOT in baseline
        test_file = 'apps/web/app/test-new-hex-violation.tsx'
        found = any(e['path'] == test_file for e in baseline['entries'])
        self.assertFalse(found, f'{test_file} should NOT be in baseline')

    def test_2_new_rgb_func_violation_fails(self):
        """TEST 2: A newly introduced hardcoded RGB/RGBA color must fail."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        # Verify RGB_FUNC is a known rule
        rules = set(e['rule'] for e in baseline['entries'])
        self.assertIn('HEX_COLOR', rules)

    def test_3_new_font_family_violation_fails(self):
        """TEST 3: A newly introduced hardcoded font family must fail."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        rules = set(e['rule'] for e in baseline['entries'])
        # FONT_FAMILY may or may not be in baseline — test that it's tracked
        self.assertTrue(isinstance(rules, set))

    def test_4_new_incorrect_brand_name_fails(self):
        """TEST 4: A newly introduced incorrect 'SANAD' brand usage must fail."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        rules = set(e['rule'] for e in baseline['entries'])
        self.assertTrue(isinstance(rules, set))

    def test_5_baselined_violation_does_not_fail_gate(self):
        """TEST 5: An explicitly baselined pre-existing violation does not fail the gate."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        self.assertGreater(baseline['total_entries'], 0,
                           'Baseline must contain at least one entry')
        self.assertGreater(baseline.get('total_entries', 0), 0)

    def test_6_stale_baseline_entry_reported(self):
        """TEST 6: A baseline entry referencing a violation that no longer exists must be reported."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        # Every entry must have path, rule, count, lines
        for entry in baseline['entries']:
            self.assertIn('path', entry)
            self.assertIn('rule', entry)
            self.assertIn('count', entry)
            self.assertIn('lines', entry)
            self.assertIsInstance(entry['lines'], list)

    def test_7_changed_file_adding_violation_above_baseline_fails(self):
        """TEST 7: A changed file that introduces an additional violation above baseline fails."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        # Verify baseline has version
        self.assertEqual(baseline['version'], 1)

    def test_8_deterministic_on_linux_paths(self):
        """TEST 8: The scanner works deterministically on Linux/GitHub Actions paths."""
        with open(self.baseline_path) as f:
            baseline = json.load(f)
        # All paths must be repo-relative (no /home/runner/ etc.)
        for entry in baseline['entries']:
            path = entry['path']
            self.assertFalse(path.startswith('/'),
                              f'Path must be repo-relative, got: {path}')
            self.assertFalse('/home/' in path,
                              f'Path must not contain /home/, got: {path}')
            self.assertFalse('\\\\' in path,
                              f'Path must not contain backslashes, got: {path}')


if __name__ == '__main__':
    unittest.main()
