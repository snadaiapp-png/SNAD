"""Repository-integrity regression tests for Git submodule/gitlink metadata."""

from __future__ import annotations

import pathlib
import subprocess


ROOT = pathlib.Path(__file__).resolve().parents[2]


def _run_git(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=check,
        text=True,
        capture_output=True,
    )


def _tracked_gitlinks() -> set[str]:
    result = _run_git("ls-files", "--stage")
    gitlinks: set[str] = set()
    for line in result.stdout.splitlines():
        metadata, path = line.split("\t", 1)
        mode = metadata.split(" ", 1)[0]
        if mode == "160000":
            gitlinks.add(path)
    return gitlinks


def _configured_submodule_paths() -> set[str]:
    gitmodules = ROOT / ".gitmodules"
    if not gitmodules.exists():
        return set()

    result = _run_git(
        "config",
        "-f",
        str(gitmodules),
        "--get-regexp",
        r"^submodule\..*\.path$",
        check=False,
    )
    if result.returncode not in (0, 1):
        raise AssertionError(f"unable to parse .gitmodules: {result.stderr.strip()}")

    paths: set[str] = set()
    for line in result.stdout.splitlines():
        _, path = line.split(maxsplit=1)
        paths.add(path)
    return paths


def test_every_tracked_gitlink_has_gitmodules_metadata() -> None:
    gitlinks = _tracked_gitlinks()
    configured = _configured_submodule_paths()
    orphaned = sorted(gitlinks - configured)

    assert orphaned == [], (
        "tracked gitlinks without .gitmodules metadata: " + ", ".join(orphaned)
    )
