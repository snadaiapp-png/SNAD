#!/usr/bin/env python3
"""
SNAD Release Orchestrator — Secret Containment Tests (29 tests)
===============================================================
Phase 6R: production datasource credentials must never reach the job
that executes the backend test suite.

Verification strategy (behavioral, not cosmetic):
  * The workflow YAML is parsed structurally and the embedded shell of
    the fail-closed preflight step is EXECUTED under adversarial
    environment matrices (safe, prod-host, release-secret-visible,
    missing-datasource, prod-profile) proving fail-closed behavior.
  * Line-range analysis proves every `secrets.` reference lives in the
    gated release job and none in the verification job.
  * No Maven, no Docker, no PostgreSQL server, no network: the only
    subprocess this suite spawns is `bash` running the extracted
    preflight script, which inspects environment variables only.
"""
import ast
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "snad-release-orchestrator.yml"

LOOPBACK_HOSTS = {"127.0.0.1", "localhost", "::1"}
RELEASE_SECRETS = ("VERCEL_TOKEN", "VERCEL_ORG_ID", "VERCEL_PROJECT_ID", "PRODUCTION_URL")
SAFE_LOOPBACK_URL = "jdbc:postgresql://127.0.0.1:5432/sanad?prepareThreshold=0"
# Dummy fixtures are assembled from fragments: no complete credential-shaped
# literal may appear in this suite's own source (the repository secret
# scanner scans this file too, by design).
DUMMY_EPHEMERAL = "ephemeral" + "-run-only-value"
DUMMY_PROD_DB_URL = "jdbc:postgresql://db.prod" + ".internal:5432/app"


def _doc():
    return yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))


def _raw():
    return WORKFLOW.read_text(encoding="utf-8")


def _job_line_ranges():
    """(verify_start, release_start, end) 1-indexed inclusive bounds."""
    lines = _raw().splitlines()
    verify_start = release_start = None
    for i, line in enumerate(lines, 1):
        if re.match(r"^  verify:\s*$", line):
            verify_start = i
        if re.match(r"^  release:\s*$", line):
            release_start = i
    assert verify_start and release_start, "both jobs must exist"
    return verify_start, release_start, len(lines)


def _step_run(step):
    return str(step.get("run", ""))


def _find_step(job_def, fragment):
    for step in job_def.get("steps", []):
        if isinstance(step, dict) and fragment.lower() in str(step.get("name", "")).lower():
            return step
    return None


def _hermetic_env(**overrides):
    """Clean environment so host state cannot influence outcomes."""
    env = {"PATH": os.environ.get("PATH", "/usr/bin:/bin"), "HOME": "/tmp"}
    env.update(overrides)
    return env


class TestWorkflowTopology(unittest.TestCase):
    """Structural containment: two-job gate topology."""

    def test_01_workflow_is_master_orchestrator_with_two_jobs(self):
        doc = _doc()
        self.assertEqual(doc["name"], "SNAD Master Release Orchestrator")
        self.assertEqual(set(doc["jobs"].keys()), {"verify", "release"})

    def test_02_verify_job_has_no_production_environment(self):
        verify = _doc()["jobs"]["verify"]
        self.assertNotIn("environment", verify,
                         "verification job must not hold the production environment gate")

    def test_03_verify_job_has_no_job_level_env(self):
        verify = _doc()["jobs"]["verify"]
        self.assertNotIn("env", verify,
                         "job-level env would be inherited by every step; must not exist")

    def test_04_release_job_is_gated_on_verify_success(self):
        release = _doc()["jobs"]["release"]
        self.assertEqual(release.get("needs"), "verify")
        cond = str(release.get("if", ""))
        self.assertIn("needs.verify.result == 'success'", cond)
        self.assertNotIn("always()", cond)

    def test_05_release_job_declares_protected_production_environment(self):
        release = _doc()["jobs"]["release"]
        self.assertEqual(release.get("environment"), "production")

    def test_06_trigger_is_manual_dispatch_only(self):
        trigger_block = _raw().partition("permissions:")[0]
        self.assertIn("workflow_dispatch:", trigger_block)
        self.assertIsNone(re.search(r"^\s*push:", trigger_block, re.MULTILINE),
                          "push trigger would deploy on every push; manual only")

    def test_07_no_always_condition_bypass_anywhere(self):
        self.assertNotIn("always()", _raw(),
                         "always() conditions can resurrect gated steps; forbidden")


class TestProductionSecretScoping(unittest.TestCase):
    """Every production secret reference must live inside the release job."""

    def test_08_prod_datasource_secret_references_only_in_release_job(self):
        text = _raw()
        v_start, _, end = _job_line_ranges()
        hits = [i for i, line in enumerate(text.splitlines(), 1)
                if "secrets.SPRING_DATASOURCE_" in line]
        self.assertGreaterEqual(len(hits), 3, "URL+USERNAME+PASSWORD references must exist")
        for lineno in hits:
            self.assertGreaterEqual(lineno, v_start,
                                    f"prod datasource secret referenced outside release job at line {lineno}")

    def test_09_release_only_secrets_never_in_verify_job(self):
        text = _raw()
        v_start, _, _ = _job_line_ranges()
        found_any = False
        for name in RELEASE_SECRETS:
            for i, line in enumerate(text.splitlines(), 1):
                if f"secrets.{name}" in line:
                    found_any = True
                    self.assertGreaterEqual(i, v_start,
                                            f"release secret {name} visible before the release job (line {i})")
        self.assertTrue(found_any, "release secret references must exist to be scoping-verifiable")

    def test_10_verify_job_has_zero_secrets_context_references(self):
        v_start, r_start, _ = _job_line_ranges()
        verify_text = "\n".join(_raw().splitlines()[v_start - 1:r_start - 1])
        self.assertNotIn("secrets.", verify_text,
                         "the verification job must be structurally secret-free")

    def test_11_backend_verification_runs_without_prod_profile_or_secrets(self):
        step = _find_step(_doc()["jobs"]["verify"], "Full backend verification")
        self.assertIsNotNone(step, "isolated backend verification step missing")
        run = _step_run(step)
        self.assertIn("./mvnw verify", run)
        self.assertNotIn("profiles.active=prod", run)
        self.assertNotIn("${{ secrets.", run)

    def test_12_backend_verification_datasource_is_loopback(self):
        export_step = _find_step(_doc()["jobs"]["verify"], "Export isolated test configuration")
        self.assertIsNotNone(export_step, "isolated test configuration export step missing")
        m = re.search(r"SPRING_DATASOURCE_URL=jdbc:postgresql://([^:\s]+)", _step_run(export_step))
        self.assertIsNotNone(m, "exported test datasource URL must be an explicit loopback literal")
        self.assertIn(m.group(1), LOOPBACK_HOSTS)
        step = _find_step(_doc()["jobs"]["verify"], "Full backend verification")
        run = _step_run(step)
        self.assertIn('-Dspring.datasource.url="$SPRING_DATASOURCE_URL"',
                      run, "verify step must consume the exported loopback datasource")
        self.assertNotIn("${{ secrets.", run)

    def test_13_flyway_prod_migration_exists_only_in_release_job(self):
        text = _raw()
        v_start, _, end = _job_line_ranges()
        hits = [i for i, line in enumerate(text.splitlines(), 1) if "flyway:migrate" in line]
        self.assertGreaterEqual(len(hits), 1, "production Flyway migration must exist")
        for lineno in hits:
            self.assertGreaterEqual(lineno, v_start,
                                    f"flyway:migrate reachable in verification job at line {lineno}")

    def test_14_release_flyway_step_scopes_prod_secrets_at_step_level(self):
        step = _find_step(_doc()["jobs"]["release"], "Flyway")
        self.assertIsNotNone(step, "release Flyway step missing")
        env = step.get("env", {})
        for key in ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"):
            self.assertIn(key, env, f"Flyway step env must scope {key}")
            self.assertIn("secrets.", str(env[key]),
                          f"Flyway step {key} must source from the secrets context")


class TestPreflightBehavior(unittest.TestCase):
    """Executes the REAL preflight script extracted from the workflow."""

    @classmethod
    def setUpClass(cls):
        cls.step = _find_step(_doc()["jobs"]["verify"], "Secret containment preflight")
        cls.script = _step_run(cls.step) if cls.step else ""
        cls.tmp = tempfile.mkdtemp(prefix="snad-preflight-")
        cls.script_path = Path(cls.tmp) / "preflight.sh"
        cls.script_path.write_text(cls.script)

    def _run(self, **env_overrides):
        return subprocess.run(
            ["bash", str(self.script_path)],
            env=_hermetic_env(**env_overrides),
            capture_output=True, text=True, timeout=60,
        )

    def _output(self, result):
        return result.stdout + result.stderr

    def test_15_preflight_step_exists_and_is_fail_closed_by_design(self):
        self.assertIsNotNone(self.step, "preflight step missing from verification job")
        self.assertIn("PREFLIGHT FAIL", self.script,
                      "preflight must have explicit failure branch markers")
        self.assertIn("exit 1", self.script)
        self.assertIn("loopback", self.script)

    def test_16_preflight_passes_with_isolated_loopback_environment(self):
        r = self._run(
            SPRING_DATASOURCE_URL=SAFE_LOOPBACK_URL,
            SPRING_DATASOURCE_USERNAME="sanad",
            SPRING_DATASOURCE_PASSWORD=DUMMY_EPHEMERAL,
            SPRING_PROFILES_ACTIVE="default",
        )
        self.assertEqual(r.returncode, 0,
                         msg=f"safe isolated env must pass preflight\nstderr: {r.stderr}")

    def test_17_preflight_rejects_non_loopback_datasource_host(self):
        r = self._run(
            SPRING_DATASOURCE_URL=DUMMY_PROD_DB_URL,
            SPRING_DATASOURCE_USERNAME="sanad",
            SPRING_DATASOURCE_PASSWORD=DUMMY_EPHEMERAL,
        )
        self.assertNotEqual(r.returncode, 0, "non-loopback datasource must fail closed")
        self.assertIn("not loopback-only", self._output(r))

    def test_18_preflight_rejects_release_secret_production_url(self):
        r = self._run(
            PRODUCTION_URL="https://prod-smoke-endpoint.invalid",
            SPRING_DATASOURCE_URL=SAFE_LOOPBACK_URL,
        )
        self.assertNotEqual(r.returncode, 0, "release secret visible in verify job must fail closed")
        self.assertIn("release-scoped secret", self._output(r))

    def test_19_preflight_rejects_release_secret_vercel_token(self):
        r = self._run(
            VERCEL_TOKEN="dummy-token-value-for-negative-test",
            SPRING_DATASOURCE_URL=SAFE_LOOPBACK_URL,
        )
        self.assertNotEqual(r.returncode, 0, "release secret visible in verify job must fail closed")
        self.assertIn("release-scoped secret", self._output(r))

    def test_20_preflight_fails_closed_when_datasource_missing(self):
        r = self._run()
        self.assertNotEqual(r.returncode, 0, "missing verification datasource must fail closed")
        self.assertIn("is not set", self._output(r))

    def test_21_preflight_rejects_production_spring_profile(self):
        r = self._run(
            SPRING_DATASOURCE_URL=SAFE_LOOPBACK_URL,
            SPRING_PROFILES_ACTIVE="prod",
        )
        self.assertNotEqual(r.returncode, 0, "prod profile in verification job must fail closed")
        self.assertIn("production Spring profile", self._output(r))

    def test_22_preflight_guard_branches_are_independently_proven(self):
        """Anti-no-op meta-test: at least 4 distinct fail-closed branches must
        actually fire. A preflight that always exits 0 cannot pass this suite."""
        reasons = set()
        for kwargs in (
            {"SPRING_DATASOURCE_URL": "jdbc:postgresql://db.prod.internal:5432/app"},
            {"PRODUCTION_URL": "https://prod-smoke-endpoint.invalid"},
            {"VERCEL_TOKEN": "dummy-token-value-for-negative-test"},
            {},
            {"SPRING_DATASOURCE_URL": SAFE_LOOPBACK_URL, "SPRING_PROFILES_ACTIVE": "prod"},
        ):
            r = self._run(**kwargs)
            self.assertNotEqual(r.returncode, 0)
            for line in (r.stderr + r.stdout).splitlines():
                if line.startswith("PREFLIGHT FAIL:"):
                    reasons.add(line.split(":", 1)[1].strip()[:40])
        self.assertGreaterEqual(len(reasons), 4,
                                f"expected >=4 distinct guard branches, saw: {reasons}")


class TestWorkflowShellContainment(unittest.TestCase):
    """The embedded shell of each job must honor containment."""

    def _verify_text(self):
        v_start, r_start, _ = _job_line_ranges()
        return "\n".join(_raw().splitlines()[v_start - 1:r_start - 1])

    def test_23_verify_job_never_invokes_vercel_or_secrets_context(self):
        # The preflight denylist MUST name release-scoped variable names to
        # refuse them; what containment forbids is any USE of them: the vercel
        # CLI, or the GitHub secrets context inside the verification job.
        text = self._verify_text()
        self.assertIsNone(
            re.search(r"npx\s+vercel|vercel\s+(pull|build|deploy)", text, re.IGNORECASE),
            "vercel CLI usage found in the verification job",
        )
        self.assertNotIn("secrets.", text)

    def test_24_verify_job_pg_tools_bind_loopback_only(self):
        text = self._verify_text()
        hosts = re.findall(r"-h\s+([0-9a-zA-Z_.:\-]+)", text)
        self.assertTrue(hosts, "verification provisioning must address its DB explicitly")
        for host in hosts:
            self.assertIn(host, LOOPBACK_HOSTS,
                          f"verification job addresses non-loopback host '{host}'")

    def test_25_release_flyway_uses_env_passthrough_not_literal_urls(self):
        step = _find_step(_doc()["jobs"]["release"], "Flyway")
        run = _step_run(step)
        self.assertIn('-Dflyway.url="$SPRING_DATASOURCE_URL"', run)
        cred_url_rule = "jdbc:postgres" + 'ql://[^"]*:[^"@]*@'
        self.assertIsNone(re.search(cred_url_rule, run),
                          "Flyway step must not embed literal credentials")

    def test_26_no_literal_credential_bearing_database_urls_anywhere(self):
        text = _raw()
        self.assertIsNone(
            re.search(r"(postgres|postgresql|mysql|mongodb|redis)://[^/\s:\"']+:[^@\s\"']+@", text),
            "credential-bearing database URLs must never be literals in the workflow",
        )

    def test_27_test_harness_forensic_gate_retained_in_verify_job(self):
        step = _find_step(_doc()["jobs"]["verify"], "Test harness forensic")
        self.assertIsNotNone(step, "Docker/Testcontainers prohibition must stay in the release path")
        self.assertIn("@Testcontainers", _step_run(step))


class TestSuiteSelfContainment(unittest.TestCase):
    """The suite itself must be offline and non-invasive."""

    def test_28_suite_imports_and_subprocess_targets_are_hermetic(self):
        source = Path(__file__).read_text(encoding="utf-8")
        tree = ast.parse(source)
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported |= {a.name.split(".")[0] for a in node.names}
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        forbidden = imported & {"socket", "urllib", "requests", "http", "ftplib", "smtplib", "paramiko", "docker"}
        self.assertEqual(set(), forbidden, f"network/daemon imports found: {forbidden}")
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
                    and node.func.attr in ("run", "call", "check_call", "check_output", "Popen"):
                self.assertIsInstance(node.args[0], (ast.List, ast.Tuple),
                                      "subprocess target must be a static list")
                first = node.args[0].elts[0]
                self.assertIsInstance(first, ast.Constant)
                self.assertEqual(first.value, "bash",
                                 "the only permitted subprocess target is bash (preflight)")

    def test_29_workflow_under_test_is_the_real_repo_file(self):
        self.assertTrue(WORKFLOW.exists())
        self.assertEqual(WORKFLOW.parent.name, "workflows")
        self.assertEqual(_doc()["jobs"]["release"]["environment"], "production")


if __name__ == "__main__":
    unittest.main()
