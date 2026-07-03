#!/usr/bin/env python3
"""
SANAD — NVD Feed Archive Utility
=================================
EXEC-PROMPT-010R12K Section 6 — independent archive utility for NVD
Feed bundles. Does NOT use create_snapshot_archive() (which adds
data/ prefix). Feed files are stored at archive root directly.

Provides:
  create_feed_archive()  — build tar.zst/.tar.gz with files at root
  list_feed_archive()    — list contents
  validate_feed_archive() — validate paths + reject special entries
  extract_feed_archive()  — safe extraction
"""
from __future__ import annotations

import hashlib
import os
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.security.nvd_archive import (
    ArchiveError,
    ArchivePathTraversalError,
    _inspect_member,
    _archive_format,
    stream_hash_file,
)


def create_feed_archive(
    archive_path: Path,
    feed_dir: Path,
    work_dir: Path | None = None,
) -> Path:
    """Create a tar.zst (or .tar.gz fallback) archive of feed files.

    Files are stored at the ROOT of the archive (no data/ prefix).
    This is the key difference from create_snapshot_archive().
    """
    archive_path = Path(archive_path)
    feed_dir = Path(feed_dir).resolve()
    work_dir = Path(work_dir) if work_dir else archive_path.parent
    work_dir.mkdir(parents=True, exist_ok=True)

    if not feed_dir.is_dir():
        raise ArchiveError(f"feed_dir does not exist: {feed_dir}")

    # Build file list (only files, no subdirectories — feed is flat)
    file_list_path = work_dir / "feed-file-list.txt"
    count = 0
    with file_list_path.open("w") as fl:
        for f in sorted(feed_dir.iterdir()):
            if f.is_file():
                rel = f.relative_to(feed_dir)
                fl.write(str(rel) + "\n")
                count += 1
    if count == 0:
        raise ArchiveError(f"no files to archive in {feed_dir}")

    # Check zstd availability
    fmt = _archive_format(archive_path)
    use_zst = fmt == "zst"
    if use_zst:
        try:
            subprocess.run(["zstd", "--version"], capture_output=True, check=True)
        except (FileNotFoundError, subprocess.CalledProcessError):
            archive_path = Path(str(archive_path)[:-len(".tar.zst")] + ".tar.gz")
            use_zst = False

    # Create archive WITHOUT --transform (no data/ prefix)
    if use_zst:
        cmd = ["tar", "--zstd", "-cf", str(archive_path),
               "-C", str(feed_dir), "-T", str(file_list_path)]
    else:
        cmd = ["tar", "-czf", str(archive_path),
               "-C", str(feed_dir), "-T", str(file_list_path)]

    try:
        subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveError(f"tar create failed: {e.stderr}") from e

    file_list_path.unlink(missing_ok=True)
    return archive_path


def list_feed_archive(archive_path: Path) -> list[str]:
    """List all entries in the feed archive."""
    archive_path = Path(archive_path)
    if not archive_path.exists():
        raise ArchiveError(f"archive not found: {archive_path}")

    fmt = _archive_format(archive_path)
    if fmt == "zst":
        cmd = ["tar", "--zstd", "-tf", str(archive_path)]
    else:
        cmd = ["tar", "-tzf", str(archive_path)]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveError(f"tar list failed: {e.stderr}") from e

    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def validate_feed_archive(archive_path: Path) -> list[str]:
    """Validate feed archive paths and entry types.

    Rejects: absolute paths, ../, symlinks, hardlinks, devices, FIFOs,
    unexpected directories, manifest.json, SHA256SUMS inside archive.
    Only allows regular files at root level.
    """
    import tempfile as _tmp

    fmt = _archive_format(archive_path)

    # For zst, decompress to temp tar first
    if fmt == "zst":
        try:
            subprocess.run(["zstd", "--version"], capture_output=True, check=True)
        except (FileNotFoundError, subprocess.CalledProcessError):
            # Fall back to name-based validation
            members = list_feed_archive(archive_path)
            safe = []
            for m in members:
                if m.startswith("/"):
                    raise ArchivePathTraversalError(f"absolute path: {m}")
                if "../" in m or m == "..":
                    raise ArchivePathTraversalError(f"parent traversal: {m}")
                if "/" in m:
                    raise ArchivePathTraversalError(f"subdirectory in feed archive: {m}")
                if m in ("manifest.json", "SHA256SUMS"):
                    raise ArchivePathTraversalError(f"sidecar file inside archive: {m}")
                safe.append(m)
            return safe

        with _tmp.NamedTemporaryFile(suffix=".tar", delete=False) as tmp:
            tmp_tar = Path(tmp.name)
        try:
            with tmp_tar.open("wb") as out:
                result = subprocess.run(["zstd", "-d", "--stdout", str(archive_path)],
                               stdout=out, check=True, stderr=subprocess.PIPE)
            tar_to_inspect = tmp_tar
        except subprocess.CalledProcessError as e:
            tmp_tar.unlink(missing_ok=True)
            raise ArchiveError(f"zstd decompression failed: {e.stderr.decode('utf-8', errors='replace') if e.stderr else ''}") from e
    else:
        tar_to_inspect = archive_path

    safe = []
    try:
        if str(tar_to_inspect).endswith(".tar.gz") or str(tar_to_inspect).endswith(".tgz"):
            with tarfile.open(tar_to_inspect, "r:gz") as tar:
                for member in tar.getmembers():
                    _validate_feed_member(member)
                    safe.append(member.name)
        else:
            with tarfile.open(tar_to_inspect, "r:") as tar:
                for member in tar.getmembers():
                    _validate_feed_member(member)
                    safe.append(member.name)
    finally:
        if fmt == "zst" and tmp_tar.exists():
            tmp_tar.unlink(missing_ok=True)

    return safe


def _validate_feed_member(member) -> None:
    """Validate a single feed archive member."""
    name = member.name

    # Path checks
    if name.startswith("/"):
        raise ArchivePathTraversalError(f"absolute path in feed archive: {name}")
    if "../" in name or name == "..":
        raise ArchivePathTraversalError(f"parent traversal: {name}")
    if "\x00" in name:
        raise ArchivePathTraversalError(f"NUL byte in path: {name}")
    if "/" in name:
        raise ArchivePathTraversalError(f"subdirectory in feed archive (files must be at root): {name}")
    if name in ("manifest.json", "SHA256SUMS"):
        raise ArchivePathTraversalError(f"sidecar file inside feed archive: {name}")

    # Type checks
    if member.issym():
        raise ArchivePathTraversalError(f"symlink in feed archive: {name}")
    if member.islnk():
        raise ArchivePathTraversalError(f"hard link in feed archive: {name}")
    if member.isdev() or member.ischr() or member.isblk():
        raise ArchivePathTraversalError(f"device file in feed archive: {name}")
    if member.isfifo():
        raise ArchivePathTraversalError(f"FIFO in feed archive: {name}")
    if member.isdir():
        raise ArchivePathTraversalError(f"directory in feed archive (files must be at root): {name}")
    if not member.isreg():
        raise ArchivePathTraversalError(f"unsupported entry type: {name}")


def extract_feed_archive(
    archive_path: Path,
    dest_dir: Path,
    validate: bool = True,
) -> Path:
    """Safely extract feed archive to dest_dir.

    Files are extracted to dest_dir root (no data/ subdirectory).
    """
    archive_path = Path(archive_path)
    dest_dir = Path(dest_dir)
    dest_dir.mkdir(parents=True, exist_ok=True)

    if not archive_path.exists():
        raise ArchiveError(f"archive not found: {archive_path}")

    if validate:
        validate_feed_archive(archive_path)

    fmt = _archive_format(archive_path)
    if fmt == "zst":
        cmd = ["tar", "--zstd", "-xf", str(archive_path), "-C", str(dest_dir),
               "--no-same-owner"]
    else:
        cmd = ["tar", "-xzf", str(archive_path), "-C", str(dest_dir),
               "--no-same-owner"]

    try:
        subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveError(f"tar extract failed: {e.stderr}") from e

    return dest_dir
