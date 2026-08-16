from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / ".github/workflows/clean-room-control-plane-audit.yml"


class CleanRoomAuditWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = AUDIT.read_text(encoding="utf-8")

    def test_contract_and_inventory_steps_capture_outcomes_without_short_circuiting(self):
        self.assertRegex(
            self.text,
            r"(?s)- name: Run all clean-room contract tests\n\s+id: contract_tests\n\s+continue-on-error: true",
        )
        self.assertRegex(
            self.text,
            r"(?s)- name: Generate sanitized fail-closed inventory\n\s+id: inventory\n\s+if: always\(\)\n\s+continue-on-error: true",
        )

    def test_final_gate_rejects_failed_contracts_or_inventory_after_artifact_upload(self):
        upload = self.text.find("- name: Upload sanitized audit evidence")
        gate = self.text.find("- name: Enforce clean-room audit gate")
        self.assertGreaterEqual(upload, 0)
        self.assertGreater(gate, upload)
        gate_region = self.text[gate:]
        self.assertIn("steps.contract_tests.outcome", gate_region)
        self.assertIn("steps.inventory.outcome", gate_region)
        self.assertRegex(gate_region, r"exit\s+1")


if __name__ == "__main__":
    unittest.main()
