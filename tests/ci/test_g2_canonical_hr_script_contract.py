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
        self.assertIn('if [ "$ACTIVE_LINK_COUNT" != "0" ]; then', text)
        self.assertIn(
            '$API_V1/organizations/$G2_ORGANIZATION_ID/legal-entity/bootstrap',
            text,
        )
        self.assertIn('"G2-ACCEPTANCE-LE"', text)
        self.assertIn('"G2 Acceptance Legal Entity"', text)
        self.assertIn("G2_EMPLOYER_CONTEXT=BOOTSTRAPPED_EMPLOYER_FOUNDATION", text)

    def test_employer_context_remains_fail_closed_on_ambiguity(self):
        text = self.text
        self.assertIn(
            'fail "G2 employer context is ambiguous: found $LEGAL_ENTITY_COUNT non-terminal canonical Employment Legal Entities"',
            text,
        )
        self.assertIn("Bootstrap employer context response has no legalEntityId", text)
        self.assertIn(
            'fail "Bootstrap employer context remains ambiguous: found $ACTIVE_LINK_COUNT active Legal Entity links"',
            text,
        )

    def test_employer_context_uses_governed_effective_date_not_stale_fixed_date(self):
        text = self.text
        self.assertIn('G2_EFFECTIVE_DATE="${G2_EFFECTIVE_DATE:-$(date -u +%F)}"', text)
        self.assertIn(
            'G2_EMPLOYMENT_START_DATE="${G2_EMPLOYMENT_START_DATE:-$(date -u -d "$G2_EFFECTIVE_DATE -1 day" +%F)}"',
            text,
        )
        self.assertIn('G2_ACTIVE_DATE="${G2_ACTIVE_DATE:-$G2_EFFECTIVE_DATE}"', text)
        self.assertNotIn('G2_EMPLOYMENT_START_DATE="2026-01-01"', text)

    def test_employment_lifecycle_dates_are_separated_and_recovery_is_bounded(self):
        text = self.text
        self.assertIn('onboarding_date="$employment_start"', text)
        self.assertIn('minimum_activation_date=$(next_day "$employment_start")', text)
        self.assertIn('if [[ "$activation_date" < "$minimum_activation_date" ]]; then', text)
        self.assertIn('[ "$version" = "1" ] || fail', text)
        self.assertIn("G2_TEMPORAL_RECOVERY=SAFE_NEXT_DAY", text)
        self.assertIn('"g2-${label,,}-submit-onboarding-v2"', text)
        self.assertIn('"g2-${label,,}-activate-v2"', text)

    def test_assignment_effective_date_stays_on_governed_release_date(self):
        text = self.text
        self.assertIn('G2_ASSIGNMENT_DATE="${G2_ASSIGNMENT_DATE:-$G2_EFFECTIVE_DATE}"', text)
        self.assertIn('--arg d "$G2_ASSIGNMENT_DATE"', text)

    def test_post_json_preserves_http_status_and_sanitized_error_envelope(self):
        text = self.text
        self.assertIn('-o "$body" -w \'%{http_code}\'', text)
        self.assertIn('failed (HTTP ${status:-000}, curl=$rc)', text)
        self.assertIn("jq -c '{code:(.code // null),message:(.message // null)}'", text)

    def test_employment_creation_uses_the_resolved_legal_entity(self):
        text = self.text
        self.assertIn('--arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID"', text)
        self.assertIn("legalEntityId:$le", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
