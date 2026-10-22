from __future__ import annotations

import unittest
from unittest.mock import patch

from scripts.ci import verify_rem_p0_006_protection as protection


class LiveProtectionVerificationTests(unittest.TestCase):
    def test_environment_requires_configured_reviewer(self):
        payload = {"protection_rules": [{"type": "required_reviewers", "reviewers": []}]}
        with patch.object(protection, "api_get", return_value=payload):
            with self.assertRaises(protection.ProtectionVerificationError):
                protection.verify_environment("owner/repo", "placeholder")

    def test_environment_requires_reviewer_rule(self):
        with patch.object(protection, "api_get", return_value={"protection_rules": []}):
            with self.assertRaises(protection.ProtectionVerificationError):
                protection.verify_environment("owner/repo", "placeholder")

    def test_legacy_protection_requires_all_controls(self):
        valid = {
            "required_pull_request_reviews": {"required_approving_review_count": 1},
            "required_status_checks": {"contexts": ["CI"]},
            "enforce_admins": {"enabled": True},
            "required_signatures": {"enabled": True},
        }
        protection.verify_legacy_branch_protection(valid)
        for key in valid:
            invalid = dict(valid)
            invalid.pop(key)
            with self.subTest(missing=key), self.assertRaises(protection.ProtectionVerificationError):
                protection.verify_legacy_branch_protection(invalid)

    def test_ruleset_requires_security_controls(self):
        rules = [
            {"type": "pull_request"},
            {"type": "required_status_checks"},
            {"type": "required_signatures"},
            {"type": "non_fast_forward"},
        ]
        with patch.object(protection, "api_get", return_value=rules):
            self.assertEqual(len(protection.verify_ruleset_branch_protection("owner/repo", "placeholder")), 4)


if __name__ == "__main__":
    unittest.main()
