#!/usr/bin/env python3
"""Regression contract for performance-budget path resolution.

The checker is invoked by multiple workflows from different working directories.
It must resolve repository assets from its own location, not from process CWD.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

import pytest


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECKER = REPO_ROOT / "scripts" / "ci" / "check-performance-budget.py"


def _fake_repo(tmp_path: Path) -> tuple[Path, Path]:
    repo = tmp_path / "repo"
    checker = repo / "scripts" / "ci" / "check-performance-budget.py"
    checker.parent.mkdir(parents=True)
    shutil.copy2(CHECKER, checker)

    web = repo / "apps" / "web"
    build = web / ".next"
    public = web / "public"
    brand = public / "assets" / "brand"
    brand.mkdir(parents=True)
    build.mkdir(parents=True)

    # Keep the fixture intentionally tiny while satisfying the checker's
    # existing fail-closed evidence requirements.
    (brand / "snad-logo.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"></svg>',
        encoding="utf-8",
    )
    (build / "build-manifest.json").write_text(
        json.dumps({"pages": {"/": []}}),
        encoding="utf-8",
    )

    return repo, checker


def _run(checker: Path, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(checker)],
        cwd=cwd,
        text=True,
        capture_output=True,
        check=False,
    )


@pytest.mark.parametrize("cwd_kind", ["repo_root", "apps_web", "arbitrary"])
def test_performance_budget_checker_is_independent_of_process_cwd(
    tmp_path: Path,
    cwd_kind: str,
) -> None:
    repo, checker = _fake_repo(tmp_path)
    web = repo / "apps" / "web"
    arbitrary = tmp_path / "outside"
    arbitrary.mkdir()

    cwd = {
        "repo_root": repo,
        "apps_web": web,
        "arbitrary": arbitrary,
    }[cwd_kind]

    result = _run(checker, cwd)

    assert result.returncode == 0, (
        f"checker must be CWD-independent for {cwd_kind}; "
        f"stdout={result.stdout!r}; stderr={result.stderr!r}"
    )
    assert "PERFORMANCE MEASUREMENT REPORT" in result.stdout
    assert "PASS — all performance budgets met with verifiable measurements" in result.stdout
