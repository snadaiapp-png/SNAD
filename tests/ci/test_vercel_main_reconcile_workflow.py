from pathlib import Path


WORKFLOW = Path(".github/workflows/vercel-main-production-reconcile.yml")
RELEASE_ROUTE = Path("apps/web/app/api/system/release/route.ts")


def test_vercel_reconcile_uses_rest_metadata_for_artifact_identity():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Verify staged production deployment via Vercel REST API" in text
    assert "https://api.vercel.com/v13/deployments/" in text
    assert 'meta.get("snadMainSha") or meta.get("githubCommitSha")' in text
    assert 'target == "production"' in text
    assert 'state == "READY"' in text
    assert "not alias_ok" in text


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
    assert "vercel@65.11.0 deploy" in text
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


def test_vercel_reconcile_stages_then_probes_before_promotion():
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "--skip-domain" in text
    assert '--production-url "$DEPLOYMENT_URL"' in text
    assert "STAGED_RELEASE_IDENTITY = PASS" in text
    assert "VERCEL_STAGED_IDENTITY = PASS" in text
    assert "Verify staged HRM G3 routes" in text
    assert "Promote tested production deployment" in text
    assert "Verify production alias still points to created deployment" in text
    assert "Verify live production routes and exact release after promotion" in text
    assert "LIVE_RELEASE_IDENTITY = PASS" in text
    sequence = [
        "Capture previous production deployment for rollback",
        "Deploy verified prebuilt artifact to Vercel Production",
        "Verify staged production deployment via Vercel REST API",
        "Verify staged HRM G3 routes",
        "Verify staged release identity before promotion",
        "Promote tested production deployment",
        "Verify production alias still points to created deployment",
        "Verify live production routes and exact release after promotion",
    ]
    offsets = [text.index(marker) for marker in sequence]
    assert offsets == sorted(offsets)


def test_production_rollback_is_fail_closed_and_scoped():
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "PREVIOUS_PRODUCTION_DEPLOYMENT_UNVERIFIED" in text
    assert "Restore previous production deployment after failed promotion" in text
    assert "if: failure() && steps.promote.outcome != 'skipped'" in text
    assert 'promote "$PREVIOUS_DEPLOYMENT_ID"' in text
    assert "PRODUCTION_ROLLBACK_IDENTITY_MISMATCH" in text
    assert "PRODUCTION_ROLLBACK_IDENTITY = PASS" in text


def test_cli_is_pinned_and_does_not_put_token_in_argv():
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "vercel@65.11.0" in text
    assert "vercel@latest" not in text
    assert "--token=" not in text
    assert "VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}" in text


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


def test_hrm_g3_routes_must_build_as_dynamic_before_production_deploy():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "HRM_G3_ROUTE_MISSING_FROM_NEXT_BUILD" in text
    assert "HRM_G3_ROUTE_NOT_DYNAMIC" in text
    assert "HRM_G3_ROUTE_STATIC_PRERENDER_REGRESSION" in text
    assert "HRM_G3_DYNAMIC_ROUTE_BUILD_CONTRACT = PASS" in text
    assert '"/hr/performance/goals"' in text
    assert '"/hr/performance/reviews"' in text


def test_reconcile_captures_route_diagnostics_before_live_g3_gate():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "Capture staged route diagnostics" in text
    assert "/hr/employees" in text
    assert "/hr/assignments" in text
    assert "/hr/performance/goals" in text
    assert "/hr/performance/reviews" in text
    assert "x-matched-path:" in text
    assert "x-snad-release-guard:" in text
    assert "production-route-diagnostics" in text
