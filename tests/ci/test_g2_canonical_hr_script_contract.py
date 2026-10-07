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

    def test_legal_entity_is_resolved_from_organization_link_not_fabricated(self):
        text = self.text
        self.assertNotIn("auth-smoke-legal-entity", text)
        self.assertNotIn('EMPLOYER_EMPLOYMENTS=$(get_json "$HR_API/employments"', text)
        self.assertIn(
            '$API_V1/organizations/$G2_ORGANIZATION_ID/legal-entity?effectiveDate=$G2_EMPLOYMENT_START_DATE',
            text,
        )
        self.assertIn(
            "G2_EMPLOYER_CONTEXT=RESOLVED_FROM_ORGANIZATION_LEGAL_ENTITY_LINK",
            text,
        )
        self.assertIn(
            "/legal-entity/bootstrap?effectiveDate=$G2_EMPLOYMENT_START_DATE",
            text,
        )
        self.assertIn("G2_EMPLOYER_CONTEXT_SOURCE=GOVERNED_BOOTSTRAP", text)
        self.assertIn("ORGANIZATION.WRITE", text)
        self.assertIn(".legalEntityId", text)

    def test_legal_entity_bootstrap_does_not_require_existing_employment(self):
        text = self.text
        bootstrap = text.split("ensure_person() {", 1)[0]
        self.assertNotIn("$HR_API/employments", bootstrap)
        self.assertIn("Employer context response has no legalEntityId", bootstrap)
        self.assertIn("Final canonical read proves the effective link exists", bootstrap)

    def test_employment_creation_uses_the_resolved_legal_entity(self):
        text = self.text
        self.assertIn('--arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID"', text)
        self.assertIn("legalEntityId:$le", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
