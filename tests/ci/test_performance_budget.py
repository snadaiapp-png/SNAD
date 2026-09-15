import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "check-performance-budget.py"
SPEC = importlib.util.spec_from_file_location("check_performance_budget", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


def test_total_static_assets_budget_is_enforced():
    data = {
        "total_js": 0,
        "largest_chunk": 0,
        "largest_chunk_name": "",
        "login_route_js": 0,
        "exec_dashboard_js": 0,
        "total_static": MODULE.BUDGETS["total_static_assets"] + 1,
        "page_bundles": {"/": {"js_files": 1, "total_js_bytes": 1}},
    }

    violations = MODULE.bundle_budget_violations(data)

    assert any("Total static assets exceeds budget" in violation for violation in violations)


def test_bundle_measurement_must_have_real_javascript_evidence():
    data = {
        "total_js": 0,
        "largest_chunk": 0,
        "largest_chunk_name": "",
        "login_route_js": 0,
        "exec_dashboard_js": 0,
        "total_static": 1,
        "page_bundles": {"/_app": {"js_files": 0, "total_js_bytes": 0}},
    }

    violations = MODULE.bundle_budget_violations(data)

    assert any("No JavaScript bundle evidence" in violation for violation in violations)
