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

    def test_legal_entity_is_resolved_from_canonical_employment_not_fabricated(self):
        text = self.text
        self.assertNotIn("auth-smoke-legal-entity", text)
        self.assertIn('EMPLOYER_EMPLOYMENTS=$(get_json "$HR_API/employments"', text)
        self.assertIn("G2_EMPLOYER_CONTEXT=RESOLVED_FROM_CANONICAL_EMPLOYMENT", text)
        self.assertIn(".legalEntityId", text)

    def test_legal_entity_resolution_fails_closed_on_zero_or_multiple_candidates(self):
        text = self.text
        self.assertIn('if [ "$LEGAL_ENTITY_COUNT" = "0" ]', text)
        self.assertIn('if [ "$LEGAL_ENTITY_COUNT" != "1" ]', text)
        self.assertIn("tenant has no non-terminal canonical Employment Legal Entity", text)
        self.assertIn("G2 employer context is ambiguous", text)

    def test_employment_creation_uses_the_resolved_legal_entity(self):
        text = self.text
        self.assertIn('--arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID"', text)
        self.assertIn("legalEntityId:$le", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
