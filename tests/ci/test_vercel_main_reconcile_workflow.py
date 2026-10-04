from pathlib import Path


WORKFLOW = Path(".github/workflows/vercel-main-production-reconcile.yml")


def test_vercel_reconcile_uses_rest_metadata_for_artifact_identity():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify exact production deployment via Vercel REST API" in text
    assert "https://api.vercel.com/v13/deployments/" in text
    assert 'meta.get("snadMainSha") or meta.get("githubCommitSha")' in text
    assert 'target == "production"' in text
    assert 'state == "READY"' in text
    assert "production_host in aliases" in text


def test_protected_route_probe_uses_ephemeral_github_oidc():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "id-token: write" in text
    assert "await core.getIDToken()" in text
    assert "VERCEL_TRUSTED_OIDC_TOKEN: ${{ steps.oidc.outputs.token }}" in text
    assert "x-vercel-trusted-oidc-idp-token" in text


def test_broken_vercel_curl_token_pattern_is_absent():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert 'vercel@latest curl "$marker_url"' not in text
    assert 'vercel@latest curl "$PRODUCTION_WEB_BASE_URL/api/system/release"' not in text
