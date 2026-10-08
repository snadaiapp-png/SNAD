#!/usr/bin/env python3
"""Fail-closed contract for production G2 canonical HR graph provisioning."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "g2" / "provision-canonical-hr.sh"


class G2CanonicalHrScriptContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SCRIPT.read_text(encoding="utf-8")

    def test_legal_entity_prefers_single_canonical_employment_then_bootstrap_fallback(self):
        text = self.text
        self.assertNotIn("auth-smoke-legal-entity", text)
        self.assertIn('EMPLOYER_EMPLOYMENTS=$(get_json "$HR_API/employments"', text)
        self.assertIn('if [ "$LEGAL_ENTITY_COUNT" = "1" ]; then', text)
        self.assertIn("G2_EMPLOYER_CONTEXT=RESOLVED_FROM_CANONICAL_EMPLOYMENT", text)
        self.assertIn('elif [ "$LEGAL_ENTITY_COUNT" = "0" ]; then', text)
        self.assertIn(
            '$API_V1/organizations/$G2_ORGANIZATION_ID/legal-entity?effectiveDate=$G2_EFFECTIVE_DATE',
            text,
        )
        self.assertIn("G2_EMPLOYER_CONTEXT=RESOLVED_FROM_ORGANIZATION_LEGAL_ENTITY_LINK", text)

    def test_employer_context_remains_fail_closed_on_ambiguity(self):
        text = self.text
        self.assertIn(
            'fail "G2 employer context is ambiguous: found $LEGAL_ENTITY_COUNT non-terminal canonical Employment Legal Entities"',
            text,
        )
        self.assertIn("Bootstrap employer context response has no legalEntityId", text)

    def test_employer_context_uses_governed_effective_date_not_stale_fixed_date(self):
        text = self.text
        self.assertIn('G2_EFFECTIVE_DATE="${G2_EFFECTIVE_DATE:-$(date -u +%F)}"', text)
        self.assertIn('G2_EMPLOYMENT_START_DATE="$G2_EFFECTIVE_DATE"', text)
        self.assertNotIn('G2_EMPLOYMENT_START_DATE="2026-01-01"', text)

    def test_employment_creation_uses_the_resolved_legal_entity(self):
        text = self.text
        self.assertIn('--arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID"', text)
        self.assertIn("legalEntityId:$le", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
