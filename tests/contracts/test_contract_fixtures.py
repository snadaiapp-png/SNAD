"""Tests for SNAD API contract fixtures and compatibility engine.

Validates that:
  * Every fixture directory has both baseline.yaml and revision.yaml
  * The oasdiff engine (pinned version 1.21.0) reports the EXPECTED outcome
    for each fixture
  * Breaking fixtures (path-removed, type-changed, required-field-added) FAIL
  * Non-breaking fixtures (optional-field-added, new-endpoint) PASS

This pytest module complements the bash wrapper at
scripts/ci/check-api-contract-compatibility.sh and is invoked by the
python-tests CI job.
"""

import json
import os
import pathlib
import shutil
import subprocess
import sys

import pytest

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
FIXTURES_DIR = REPO_ROOT / "tests" / "contracts"

# Pinned version — must match scripts/ci/check-api-contract-compatibility.sh
OASDIFF_VERSION = "1.21.0"


def _oasdiff_binary():
    """Return path to oasdiff binary, installing it on first use if needed."""
    on_path = shutil.which("oasdiff")
    if on_path:
        return on_path
    install_dir = pathlib.Path.home() / ".cache" / "oasdiff" / OASDIFF_VERSION
    bin_path = install_dir / "oasdiff"
    if bin_path.exists() and os.access(bin_path, os.X_OK):
        return str(bin_path)
    install_dir.mkdir(parents=True, exist_ok=True)
    url = (
        "https://github.com/oasdiff/oasdiff/releases/download/"
        f"v{OASDIFF_VERSION}/oasdiff_{OASDIFF_VERSION}_linux_amd64.tar.gz"
    )
    archive = install_dir / "oasdiff.tar.gz"
    subprocess.check_call(
        ["curl", "-fsSL", "-o", str(archive), url],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.check_call(
        ["tar", "-xzf", str(archive), "-C", str(install_dir), "oasdiff"]
    )
    bin_path.chmod(0o755)
    archive.unlink()
    return str(bin_path)


def _run_oasdiff(baseline: pathlib.Path, revision: pathlib.Path) -> int:
    """Run oasdiff breaking; return exit code (0=non-breaking, 1=breaking)."""
    binary = _oasdiff_binary()
    result = subprocess.run(
        [binary, "breaking", "--fail-on=ERR", "--format=text",
         str(baseline), str(revision)],
        capture_output=True,
        text=True,
    )
    return result.returncode


def _discover_fixtures(category: str):
    """Return list of (name, baseline_path, revision_path) for category."""
    base_dir = FIXTURES_DIR / category
    if not base_dir.exists():
        return []
    fixtures = []
    for d in sorted(base_dir.iterdir()):
        if not d.is_dir():
            continue
        b = d / "baseline.yaml"
        r = d / "revision.yaml"
        if b.exists() and r.exists():
            fixtures.append((d.name, b, r))
    return fixtures


BREAKING_FIXTURES = _discover_fixtures("breaking")
NONBREAKING_FIXTURES = _discover_fixtures("non-breaking")


def test_fixtures_directory_exists():
    assert FIXTURES_DIR.exists(), f"fixtures dir missing: {FIXTURES_DIR}"
    assert (FIXTURES_DIR / "breaking").exists()
    assert (FIXTURES_DIR / "non-breaking").exists()


def test_at_least_one_breaking_fixture_per_required_case():
    required = {"path-removed", "type-changed", "required-field-added"}
    found = {name for name, _, _ in BREAKING_FIXTURES}
    missing = required - found
    assert not missing, f"missing required breaking fixtures: {missing}"


def test_at_least_one_non_breaking_fixture_per_required_case():
    required = {"optional-field-added", "new-endpoint"}
    found = {name for name, _, _ in NONBREAKING_FIXTURES}
    missing = required - found
    assert not missing, f"missing required non-breaking fixtures: {missing}"


@pytest.mark.parametrize(
    "name,baseline,revision",
    BREAKING_FIXTURES,
    ids=[f"breaking-{n}" for n, _, _ in BREAKING_FIXTURES],
)
def test_breaking_fixtures_actually_break(name, baseline, revision):
    """Each breaking fixture must produce oasdiff exit code 1."""
    rc = _run_oasdiff(baseline, revision)
    assert rc == 1, (
        f"breaking fixture {name} expected oasdiff exit 1, got {rc}. "
        f"This means the engine failed to detect the breaking change."
    )


@pytest.mark.parametrize(
    "name,baseline,revision",
    NONBREAKING_FIXTURES,
    ids=[f"non-breaking-{n}" for n, _, _ in NONBREAKING_FIXTURES],
)
def test_non_breaking_fixtures_pass(name, baseline, revision):
    """Each non-breaking fixture must produce oasdiff exit code 0."""
    rc = _run_oasdiff(baseline, revision)
    assert rc == 0, (
        f"non-breaking fixture {name} expected oasdiff exit 0, got {rc}. "
        f"This means the engine flagged a non-breaking change as breaking."
    )


def test_baseline_metadata_file_is_valid_json():
    meta_path = REPO_ROOT / "docs" / "api-contracts" / "openapi-v1-baseline-metadata.json"
    assert meta_path.exists(), "baseline metadata file missing"
    data = json.loads(meta_path.read_text())
    required_keys = {
        "generatedFromCommit", "generatedAt", "generator",
        "pathCount", "schemaCount", "approvedBy", "baselineVersion",
    }
    assert required_keys.issubset(data.keys())
    assert data["baselineVersion"] == "v1"
    assert isinstance(data["pathCount"], int) and data["pathCount"] > 0
    assert isinstance(data["schemaCount"], int) and data["schemaCount"] > 0
    assert len(data["generatedFromCommit"]) >= 20


def test_baseline_yaml_matches_metadata_counts():
    """The committed baseline YAML must have the path/schema counts claimed
    in the metadata file. Catches drift if someone edits one but not the other."""
    try:
        import yaml
    except ImportError:
        pytest.skip("PyYAML not installed")
    meta = json.loads(
        (REPO_ROOT / "docs" / "api-contracts" / "openapi-v1-baseline-metadata.json").read_text()
    )
    baseline = (REPO_ROOT / "docs" / "api-contracts" / "openapi-v1-baseline.yaml").read_text()
    doc = yaml.safe_load(baseline)
    assert len(doc.get("paths", {})) == meta["pathCount"], (
        f"path count drift: metadata={meta['pathCount']}, baseline={len(doc.get('paths', {}))}"
    )
    assert len(doc.get("components", {}).get("schemas", {})) == meta["schemaCount"], (
        f"schema count drift: metadata={meta['schemaCount']}, "
        f"baseline={len(doc.get('components', {}).get('schemas', {}))}"
    )
