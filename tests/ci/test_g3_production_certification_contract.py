import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/g3-production-certification.yml"
SPEC = ROOT / "apps/web/e2e/g3-production-readonly.spec.ts"
G2 = ROOT / ".github/workflows/g2-production-identity-provisioning.yml"

def test_g3_production_certification_is_manual_and_read_only():
    workflow = WORKFLOW.read_text(encoding="utf-8")
    spec = SPEC.read_text(encoding="utf-8")
    assert "workflow_dispatch:" in workflow
    assert "\n  push:" not in workflow
    assert "\n  pull_request:" not in workflow
    assert "environment: production" in workflow
    assert "api/system/release" in workflow
    assert "G3_EXPECTED_SHA" in workflow
    assert "g3-production-readonly.spec.ts" in (ROOT / "apps/web/playwright-g3-production.config.ts").read_text(encoding="utf-8")
    forbidden_mutations = [".post(", ".patch(", ".put(", ".delete(", "reviews-create-save", "progress-save-"]
    assert all(marker not in spec for marker in forbidden_mutations)

def test_g3_qa_role_capability_partition_is_explicit():
    text = G2.read_text(encoding="utf-8")
    for capability in [
        "HRM.PERFORMANCE.GOAL.SELF_VIEW",
        "HRM.PERFORMANCE.GOAL.SELF_UPDATE",
        "HRM.PERFORMANCE.REVIEW.SELF_VIEW",
        "HRM.PERFORMANCE.REVIEW.SELF_SUBMIT",
        "HRM.PERFORMANCE.GOAL.TEAM_MANAGE",
        "HRM.PERFORMANCE.REVIEW.TEAM_MANAGE",
    ]:
        assert capability in text
    assert "G3_NEGATIVE_PRINCIPAL=PASS" in text
    assert 'startswith("HRM.PERFORMANCE.")' in text

def test_g3_production_spec_is_excluded_from_generic_playwright_matrices():
    for relative in ["apps/web/playwright.config.ts", "apps/web/playwright.standard.config.ts"]:
        text = (ROOT / relative).read_text(encoding="utf-8")
        assert '"**/g3-production-readonly.spec.ts"' in text

if __name__ == "__main__":
    test_g3_production_certification_is_manual_and_read_only()
    test_g3_qa_role_capability_partition_is_explicit()
    test_g3_production_spec_is_excluded_from_generic_playwright_matrices()
    print("G3_PRODUCTION_CERTIFICATION_CONTRACT=PASS")
