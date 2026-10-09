#!/usr/bin/env python3
"""Fail-closed contract for production G2 canonical HR graph provisioning."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "g2" / "provision-canonical-hr.sh"


class G2CanonicalHrScriptContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SCRIPT.read_text(encoding="utf-8")

    def _lifecycle_block(self, status):
        """Extract the `if [ "$status" = ... ]; then ... fi` lifecycle block."""
        match = re.search(
            r'if \[ "\$status" = "' + re.escape(status) + r'" \]; then\n(.*?)\n  fi\n',
            self.text,
            re.S,
        )
        self.assertIsNotNone(match, f"lifecycle block for {status} not found")
        return match.group(1)

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
        self.assertIn('G2_EMPLOYMENT_START_DATE="$G2_ONBOARDING_DATE"', text)
        self.assertNotIn('G2_EMPLOYMENT_START_DATE="2026-01-01"', text)

    def test_employment_creation_uses_the_resolved_legal_entity(self):
        text = self.text
        self.assertIn('--arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID"', text)
        self.assertIn("legalEntityId:$le", text)

    # ------------------------------------------------------------------
    # Employment lifecycle date contract — production 409 root-cause fix
    # (run 37916770147: same-day submit-onboarding + activate produced
    # effective_to < effective_from and violated ck_hr_employment_status_dates).
    # ------------------------------------------------------------------

    def test_onboarding_and_activation_dates_are_deterministic_and_distinct(self):
        text = self.text
        self.assertIn('G2_ACTIVE_DATE="$G2_EFFECTIVE_DATE"', text)
        self.assertIn(
            'G2_ONBOARDING_DATE="$(date -u -d "$G2_EFFECTIVE_DATE - 1 day" +%F)"',
            text,
        )
        # The two dates must never collapse onto one another again.
        self.assertNotIn('G2_ONBOARDING_DATE="$G2_EFFECTIVE_DATE"', text)
        self.assertNotIn('G2_ACTIVE_DATE="$G2_ONBOARDING_DATE"', text)

    def test_onboarding_date_is_exactly_activation_date_minus_one_day(self):
        text = self.text
        self.assertIn('date -u -d "$G2_EFFECTIVE_DATE - 1 day" +%F', text)
        self.assertIn('G2_ACTIVE_DATE="$G2_EFFECTIVE_DATE"', text)
        self.assertIn('G2_ONBOARDING_DATE="$(date -u -d "$G2_EFFECTIVE_DATE - 1 day" +%F)"', text)

    def test_submit_onboarding_uses_onboarding_date_exclusively(self):
        draft_block = self._lifecycle_block("DRAFT")
        self.assertIn(
            'post_json "$HR_API/employments/$employment_id/submit-onboarding"',
            draft_block,
        )
        self.assertIn('--arg d "$G2_ONBOARDING_DATE"', draft_block)
        self.assertNotIn("$G2_ACTIVE_DATE", draft_block)
        self.assertNotIn("$G2_EFFECTIVE_DATE", draft_block)
        self.assertNotIn("$G2_EMPLOYMENT_START_DATE", draft_block)

    def test_activate_still_uses_governed_active_date_exclusively(self):
        pending_block = self._lifecycle_block("PENDING_ONBOARDING")
        self.assertIn(
            'post_json "$HR_API/employments/$employment_id/activate"',
            pending_block,
        )
        self.assertIn('--arg d "$G2_ACTIVE_DATE"', pending_block)
        self.assertNotIn("$G2_ONBOARDING_DATE", pending_block)
        self.assertNotIn("$G2_EFFECTIVE_DATE", pending_block)

    def test_employment_start_date_is_consistent_with_onboarding_date(self):
        text = self.text
        self.assertIn('--arg start "$G2_EMPLOYMENT_START_DATE"', text)
        # employmentStartDate equals the onboarding effective date so the
        # PENDING_ONBOARDING period never precedes the employment record and
        # activation (G2_ACTIVE_DATE) always opens after employment start.
        self.assertIn('G2_EMPLOYMENT_START_DATE="$G2_ONBOARDING_DATE"', text)
        self.assertNotIn('G2_EMPLOYMENT_START_DATE="$G2_EFFECTIVE_DATE"', text)

    def test_same_day_submit_and_activate_cannot_reenter(self):
        text = self.text
        # Onboarding is derived deterministically as activation date - 1 day,
        # so both transitions can never share one effective date again.
        self.assertIn('G2_ONBOARDING_DATE="$(date -u -d "$G2_EFFECTIVE_DATE - 1 day" +%F)"', text)
        draft_block = self._lifecycle_block("DRAFT")
        pending_block = self._lifecycle_block("PENDING_ONBOARDING")
        self.assertIn('"$G2_ONBOARDING_DATE"', draft_block)
        self.assertIn('"$G2_ACTIVE_DATE"', pending_block)
        self.assertNotIn('"$G2_ONBOARDING_DATE"', pending_block)
        self.assertNotIn('"$G2_ACTIVE_DATE"', draft_block)


if __name__ == "__main__":
    unittest.main(verbosity=2)
