from pathlib import Path


WORKFLOW = Path(".github/workflows/vercel-main-production-reconcile.yml")
RELEASE_ROUTE = Path("apps/web/app/api/system/release/route.ts")


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


def test_vercel_reconcile_deploys_the_verified_prebuilt_artifact():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Deploy verified prebuilt artifact to Vercel Production" in text
    assert "vercel@latest deploy" in text
    assert "--prebuilt" in text
    assert "--prod" in text
    assert '--meta "snadMainSha=$GITHUB_SHA"' in text
    assert '--meta "snadMainRef=main"' in text
    assert '--meta "snadDeploymentMethod=verified-prebuilt"' in text
    assert '--env "SNAD_RELEASE_SHA=$GITHUB_SHA"' in text
    assert '--env "SNAD_RELEASE_REF=main"' in text
    assert "VERCEL_VERIFIED_PREBUILT_DEPLOYMENT = PASS" in text
    assert '"gitSource": {' not in text
    assert '"snadDeploymentMethod": "git-source-rest"' not in text


def test_prebuilt_release_identity_has_explicit_runtime_fallbacks():
    workflow = WORKFLOW.read_text(encoding="utf-8")
    release = RELEASE_ROUTE.read_text(encoding="utf-8")

    assert "SNAD_RELEASE_SHA" in workflow
    assert "SNAD_RELEASE_REF" in workflow
    assert "process.env.SNAD_RELEASE_SHA" in release
    assert "process.env.SNAD_RELEASE_REF" in release
    assert "process.env.VERCEL_GIT_COMMIT_SHA" in release
    assert "process.env.VERCEL_GIT_COMMIT_REF" in release
    assert release.index("process.env.SNAD_RELEASE_SHA") < release.index("process.env.VERCEL_GIT_COMMIT_SHA")
    assert release.index("process.env.SNAD_RELEASE_REF") < release.index("process.env.VERCEL_GIT_COMMIT_REF")


def test_vercel_reconcile_fails_closed_on_monorepo_and_route_artifact_drift():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify Vercel monorepo project settings" in text
    assert 'root_directory != "apps/web"' in text
    assert "sourceFilesOutsideRootDirectory" in text
    assert "VERCEL_MONOREPO_PROJECT_SETTINGS = PASS" in text
    assert "HRM_G3_ROUTE_ARTIFACT_MISSING" in text
    assert "HRM_G3_PREBUILT_ROUTE_ARTIFACTS = PASS" in text
    assert "find .vercel/output -type f | grep -E 'hr/performance/(goals|reviews)' || true" not in text


def test_vercel_reconcile_probes_live_routes_only_after_alias_identity():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify HRM G3 routes on created deployment" not in text
    assert '--production-url "$DEPLOYMENT_URL"' not in text
    assert "hrm-g3-deployment-routes.json" not in text
    assert "Verify live HRM G3 routes" in text
    assert '--production-url "$PRODUCTION_WEB_BASE_URL"' in text
    assert "Verify production alias still points to created deployment" in text
    assert "PRODUCTION_ALIAS_DEPLOYMENT_DRIFT" in text
    assert "PRODUCTION_ALIAS_CREATED_DEPLOYMENT = PASS" in text


def test_prebuilt_deploy_bypasses_vercelignore_only_for_output_upload_and_restores_it():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert 'vercelignore_backup="$RUNNER_TEMP/vercelignore.prebuilt.backup"' in text
    assert "restore_vercelignore()" in text
    assert "rm .vercelignore" in text
    assert "trap restore_vercelignore EXIT" in text
    assert "restore_vercelignore" in text
    assert "trap - EXIT" in text
    assert "VERCEL_PREBUILT_IGNORE_BYPASS = ACTIVE" in text
    assert "VERCEL_PREBUILT_IGNORE_BYPASS = PASS" in text
    assert "VERCELIGNORE_RESTORE_FAILED" in text
