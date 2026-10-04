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


def test_vercel_reconcile_creates_exact_sha_git_source_deployment_without_upload():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Create exact-SHA Git-source Production deployment via Vercel REST API" in text
    assert '"gitSource": {' in text
    assert '"type": "github"' in text
    assert '"ref": "main"' in text
    assert '"sha": expected' in text
    assert '"target": "production"' in text
    assert '"project": project_id' in text
    assert '"snadDeploymentMethod": "git-source-rest"' in text
    assert "vercel@latest deploy" not in text
    assert "--prebuilt" not in text
    assert "vercel-deploy.log" not in text


def test_git_source_deploy_resolves_required_project_name_and_logs_http_errors():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "https://api.vercel.com/v9/projects/" in text
    assert 'project_name = str(project.get("name") or "").strip()' in text
    assert '"name": project_name' in text
    assert '"project": project_id' in text
    assert "VERCEL_PROJECT_LOOKUP_HTTP_" in text
    assert "VERCEL_GIT_SOURCE_DEPLOYMENT_HTTP_" in text
    assert 'error.read().decode("utf-8", errors="replace")' in text


def test_vercel_reconcile_fails_closed_on_monorepo_and_route_artifact_drift():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify Vercel monorepo project settings" in text
    assert 'root_directory != "apps/web"' in text
    assert "sourceFilesOutsideRootDirectory" in text
    assert "VERCEL_MONOREPO_PROJECT_SETTINGS = PASS" in text
    assert "HRM_G3_ROUTE_ARTIFACT_MISSING" in text
    assert "HRM_G3_PREBUILT_ROUTE_ARTIFACTS = PASS" in text
    assert "find .vercel/output -type f | grep -E 'hr/performance/(goals|reviews)' || true" not in text


def test_vercel_reconcile_proves_unique_deployment_before_final_alias_identity():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify HRM G3 routes on created deployment" in text
    assert '--production-url "$DEPLOYMENT_URL"' in text
    assert "hrm-g3-deployment-routes.json" in text
    assert "Verify production alias still points to created deployment" in text
    assert "PRODUCTION_ALIAS_DEPLOYMENT_DRIFT" in text
    assert "PRODUCTION_ALIAS_CREATED_DEPLOYMENT = PASS" in text
