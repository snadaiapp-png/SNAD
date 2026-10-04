import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "operations" / "vercel_main_production_reconcile.py"


def load_module():
    spec = importlib.util.spec_from_file_location("vercel_main_production_reconcile", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_create_exact_main_deployment_sends_boolean_dirty(monkeypatch):
    module = load_module()
    calls = []

    def fake_request_json(method, url, token=None, payload=None, timeout=60):
        calls.append((method, url, payload))
        if method == "GET":
            return {"name": "snad-app"}
        return {"id": "dpl_test"}

    monkeypatch.setattr(module, "request_json", fake_request_json)

    deployment_id = module.create_exact_main_deployment(
        token="token",
        project_id="prj_test",
        team_id="team_test",
        repository="snadaiapp-png/SNAD",
        release_sha="9c2f1d4a333290d3503de99dbfc5c4d62408c3f9",
    )

    assert deployment_id == "dpl_test"
    post_payload = calls[1][2]
    assert post_payload["gitMetadata"]["dirty"] is False
    assert isinstance(post_payload["gitMetadata"]["dirty"], bool)
