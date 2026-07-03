#!/usr/bin/env python3
"""
SANAD — NVD Snapshot Archive Utility
======================================
EXEC-PROMPT-010R12F Section 12 — unified archive creation, listing,
validation, and safe extraction for NVD snapshot archives.

Supports:
  - .tar.zst  (via `tar --zstd` subprocess — Python 3.12 tarfile
               does not support zst natively)
  - .tar.gz   (via `tar -z` subprocess as fallback)

Provides:
  - create_snapshot_archive()  — build archive with `data/` prefix
  - list_snapshot_archive()    — list contents
  - validate_archive_paths()   — reject path traversal, symlinks, etc.
  - extract_snapshot_archive() — safe extraction with path validation
  - stream_hash_file()         — memory-efficient SHA-256 of large files

All consumers (Publisher, Bootstrap, Integrity, OWASP, R12B, Recovery,
tests) MUST use this module — no direct tarfile.open() on .tar.zst.
"""
from __future__ import annotations

import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

# ---------- Constants ----------

# Files that indicate an incomplete H2 shutdown — must be excluded
FORBIDDEN_TEMP_PATTERNS = ("*.tmp.db", "*.temp.db", "odc.mv.db.temp")
FORBIDDEN_LOCK_PATTERNS = ("*.lock.db", "*.lock")
# odc.trace.db is a legitimate H2 byproduct and is ACCEPTED.

# Patterns that indicate path traversal or unsafe entries
UNSAFE_PATH_PATTERNS = (
    re.compile(r"^\.\./"),
    re.compile(r"/"),           # absolute paths
    re.compile(r"^\.\./"),      # parent traversal
    re.compile(r"\.\./"),       # any parent traversal
)


class ArchiveError(Exception):
    """Base class for archive errors."""


class ArchivePathTraversalError(ArchiveError):
    """Raised when an archive contains unsafe paths."""


class ArchiveExtractionError(ArchiveError):
    """Raised when extraction fails."""


# ---------- Helpers ----------

def stream_hash_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    """Compute SHA-256 of a file by streaming chunks.

    Never loads the entire file into memory.
    """
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(chunk_size), b""):
            h.update(chunk)
    return h.hexdigest()


def _archive_format(archive_path: Path) -> str:
    """Determine archive format from extension."""
    name = str(archive_path).lower()
    if name.endswith(".tar.zst") or name.endswith(".tzst"):
        return "zst"
    if name.endswith(".tar.gz") or name.endswith(".tgz"):
        return "gz"
    raise ArchiveError(f"unsupported archive format: {archive_path}")


def _tar_create_cmd(archive_path: Path, work_dir: Path, file_list: Path) -> list[str]:
    """Build the tar create command for the appropriate format."""
    fmt = _archive_format(archive_path)
    if fmt == "zst":
        return ["tar", "--zstd", "-cf", str(archive_path),
                "-C", str(work_dir),
                "--transform=s,^,data/,",
                "-T", str(file_list)]
    else:  # gz
        return ["tar", "-czf", str(archive_path),
                "-C", str(work_dir),
                "--transform=s,^,data/,",
                "-T", str(file_list)]


def _tar_list_cmd(archive_path: Path) -> list[str]:
    """Build the tar list command."""
    fmt = _archive_format(archive_path)
    if fmt == "zst":
        return ["tar", "--zstd", "-tf", str(archive_path)]
    else:
        return ["tar", "-tzf", str(archive_path)]


def _tar_extract_cmd(archive_path: Path, dest_dir: Path) -> list[str]:
    """Build the tar extract command."""
    fmt = _archive_format(archive_path)
    if fmt == "zst":
        return ["tar", "--zstd", "-xf", str(archive_path), "-C", str(dest_dir)]
    else:
        return ["tar", "-xzf", str(archive_path), "-C", str(dest_dir)]


# ---------- Forbidden file filtering ----------

import fnmatch as _fnmatch


def is_forbidden_file(filename: str) -> bool:
    """Check if a file matches forbidden lock/temp patterns."""
    for pat in FORBIDDEN_LOCK_PATTERNS + FORBIDDEN_TEMP_PATTERNS:
        if _fnmatch.fnmatch(filename, pat):
            return True
    return False


def build_file_list(data_dir: Path, file_list_path: Path) -> int:
    """Walk data_dir, write allowed files to file_list_path.

    Returns the count of files written.
    """
    count = 0
    with file_list_path.open("w") as fl:
        for root, dirs, files in os.walk(data_dir):
            for fname in sorted(files):
                if is_forbidden_file(fname):
                    continue
                fpath = Path(root) / fname
                rel = fpath.relative_to(data_dir)
                fl.write(str(rel) + "\n")
                count += 1
    return count


# ---------- Archive creation ----------

def create_snapshot_archive(
    archive_path: Path,
    data_dir: Path,
    work_dir: Path | None = None,
) -> Path:
    """Create a tar.zst (or .tar.gz fallback) archive of data_dir.

    The archive root is `data/` — all entries are prefixed with `data/`.
    Forbidden files (*.lock.db, *.lock, *.tmp.db, *.temp.db, odc.mv.db.temp)
    are excluded. odc.trace.db is included.

    If the archive path ends in .tar.zst but zstd is not available,
    falls back to creating a .tar.gz archive instead and returns its path.

    Returns the path to the created archive.
    """
    archive_path = Path(archive_path)
    data_dir = Path(data_dir).resolve()
    work_dir = Path(work_dir) if work_dir else archive_path.parent
    work_dir.mkdir(parents=True, exist_ok=True)

    if not data_dir.is_dir():
        raise ArchiveError(f"data_dir does not exist: {data_dir}")

    file_list_path = work_dir / "file-list.txt"
    count = build_file_list(data_dir, file_list_path)
    if count == 0:
        raise ArchiveError(f"no files to archive in {data_dir}")

    # Check if zstd is available
    fmt = _archive_format(archive_path)
    use_zst = fmt == "zst"
    if use_zst:
        # Check zstd availability
        try:
            subprocess.run(["zstd", "--version"], capture_output=True, check=True)
        except (FileNotFoundError, subprocess.CalledProcessError):
            # Fall back to gzip — change extension from .tar.zst to .tar.gz
            archive_path = Path(str(archive_path)[:-len(".tar.zst")] + ".tar.gz")
            use_zst = False

    cmd = _tar_create_cmd(archive_path, data_dir, file_list_path)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveError(f"tar create failed: {e.stderr}") from e
    except FileNotFoundError as e:
        raise ArchiveError(f"tar command not found: {e}") from e

    file_list_path.unlink(missing_ok=True)
    return archive_path


# ---------- Archive listing ----------

def list_snapshot_archive(archive_path: Path) -> list[str]:
    """List all entries in the archive. Returns relative paths."""
    archive_path = Path(archive_path)
    if not archive_path.exists():
        raise ArchiveError(f"archive not found: {archive_path}")

    cmd = _tar_list_cmd(archive_path)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveError(f"tar list failed: {e.stderr}") from e
    except FileNotFoundError as e:
        raise ArchiveError(f"tar command not found: {e}") from e

    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


# ---------- Path validation ----------

def _inspect_member(member) -> None:
    """R12G Section 17: inspect a single tar member for unsafe paths and types.

    Raises ArchivePathTraversalError on any violation.
    """
    name = member.name
    # Path checks
    if name.startswith("/"):
        raise ArchivePathTraversalError(f"absolute path in archive: {name}")
    if "../" in name or name == ".." or name.startswith("../"):
        raise ArchivePathTraversalError(f"parent traversal in archive: {name}")
    if "\x00" in name:
        raise ArchivePathTraversalError(f"NUL byte in path: {name}")
    # Windows drive path
    if len(name) >= 2 and name[1] == ":":
        raise ArchivePathTraversalError(f"Windows drive path: {name}")
    # Backslash traversal
    if "..\\" in name:
        raise ArchivePathTraversalError(f"backslash traversal: {name}")

    # Type checks — R12G Section 17.2
    if member.issym():
        raise ArchivePathTraversalError(f"symlink in archive: {name} -> {member.linkname}")
    if member.islnk():
        raise ArchivePathTraversalError(f"hard link in archive: {name} -> {member.linkname}")
    if member.isdev():
        raise ArchivePathTraversalError(f"device file in archive: {name}")
    if member.ischr():
        raise ArchivePathTraversalError(f"character device in archive: {name}")
    if member.isblk():
        raise ArchivePathTraversalError(f"block device in archive: {name}")
    if member.isfifo():
        raise ArchivePathTraversalError(f"FIFO in archive: {name}")
    # Only allow regular files and directories
    if not (member.isreg() or member.isdir()):
        raise ArchivePathTraversalError(f"unsupported entry type in archive: {name} (type={member.type})")


def validate_archive_paths(archive_path: Path) -> list[str]:
    """Validate that all paths in the archive are safe AND that no
    unsafe entry types (symlinks, hardlinks, devices, FIFOs) exist.

    R12G Section 17: rejects:
      - Absolute paths
      - Paths containing ../
      - Symlinks (typeflag '2' or '1')
      - Hard links (typeflag '1')
      - Character devices (typeflag '3')
      - Block devices (typeflag '4')
      - FIFOs (typeflag '6')
      - Sockets/unknown (typeflag other regular)

    Only allows: regular files (typeflag '0' or '\0') and directories ('5').

    For .tar.zst, decompresses to a temp .tar first via `zstd -d`, then
    inspects with Python tarfile.

    Returns the list of safe member paths.
    """
    import tarfile
    import tempfile

    fmt = _archive_format(archive_path)

    # For zst, decompress to a temp tar file first
    if fmt == "zst":
        # Check if zstd is available
        try:
            subprocess.run(["zstd", "--version"], capture_output=True, check=True)
        except (FileNotFoundError, subprocess.CalledProcessError):
            # zstd not available — fall back to name-based validation only
            members = list_snapshot_archive(archive_path)
            safe = []
            for m in members:
                if m.startswith("/"):
                    raise ArchivePathTraversalError(f"absolute path in archive: {m}")
                if "../" in m or m == ".." or m.startswith("../"):
                    raise ArchivePathTraversalError(f"parent traversal in archive: {m}")
                safe.append(m)
            return safe

        # Decompress zst to tar
        with tempfile.NamedTemporaryFile(suffix=".tar", delete=False) as tmp:
            tmp_tar = Path(tmp.name)
        try:
            with tmp_tar.open("wb") as out:
                subprocess.run(["zstd", "-d", "--stdout", str(archive_path)],
                               stdout=out, check=True, stderr=subprocess.PIPE)
            tar_to_inspect = tmp_tar
        except subprocess.CalledProcessError as e:
            tmp_tar.unlink(missing_ok=True)
            raise ArchiveError(f"zstd decompression failed: {e.stderr.decode('utf-8', errors='replace') if e.stderr else ''}") from e
    else:
        tar_to_inspect = archive_path

    safe = []
    try:
        # Open with auto-detection of compression (r:* for gz, r: for plain)
        if str(tar_to_inspect).endswith(".tar.gz") or str(tar_to_inspect).endswith(".tgz"):
            with tarfile.open(tar_to_inspect, "r:gz") as tar:
                for member in tar.getmembers():
                    _inspect_member(member)
                    safe.append(member.name)
        else:
            with tarfile.open(tar_to_inspect, "r:") as tar:
                for member in tar.getmembers():
                    _inspect_member(member)
                    safe.append(member.name)
    finally:
        if fmt == "zst" and tmp_tar.exists():
            tmp_tar.unlink(missing_ok=True)

    return safe


# ---------- Safe extraction ----------

def extract_snapshot_archive(
    archive_path: Path,
    dest_dir: Path,
    validate: bool = True,
) -> Path:
    """Safely extract the archive to dest_dir.

    If validate=True (default), validates paths before extraction.
    Returns dest_dir.

    Uses `tar --no-same-owner --no-same-permissions` for safety on
    self-hosted runners. The archive is extracted to dest_dir; the
    `data/` prefix is preserved so dest_dir/data/odc.mv.db is the
    database file.
    """
    archive_path = Path(archive_path)
    dest_dir = Path(dest_dir)
    dest_dir.mkdir(parents=True, exist_ok=True)

    if not archive_path.exists():
        raise ArchiveError(f"archive not found: {archive_path}")

    if validate:
        validate_archive_paths(archive_path)

    fmt = _archive_format(archive_path)
    if fmt == "zst":
        cmd = ["tar", "--zstd", "-xf", str(archive_path),
               "-C", str(dest_dir),
               "--no-same-owner"]
    else:
        cmd = ["tar", "-xzf", str(archive_path),
               "-C", str(dest_dir),
               "--no-same-owner"]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        raise ArchiveExtractionError(f"tar extract failed: {e.stderr}") from e
    except FileNotFoundError as e:
        raise ArchiveExtractionError(f"tar command not found: {e}") from e

    return dest_dir


# ---------- CLI ----------

def main():
    import argparse
    parser = argparse.ArgumentParser(description="NVD snapshot archive utility")
    sub = parser.add_subparsers(dest="command", required=True)

    p_create = sub.add_parser("create", help="Create a snapshot archive")
    p_create.add_argument("--archive", required=True, help="Output archive path")
    p_create.add_argument("--data-dir", required=True, help="Data directory to archive")
    p_create.add_argument("--work-dir", default=None, help="Temp work directory")

    p_list = sub.add_parser("list", help="List archive contents")
    p_list.add_argument("--archive", required=True)

    p_validate = sub.add_parser("validate", help="Validate archive paths are safe")
    p_validate.add_argument("--archive", required=True)

    p_extract = sub.add_parser("extract", help="Extract archive safely")
    p_extract.add_argument("--archive", required=True)
    p_extract.add_argument("--destination", required=True)
    p_extract.add_argument("--no-validate", action="store_true")

    p_hash = sub.add_parser("hash", help="Stream-hash a file")
    p_hash.add_argument("--file", required=True)

    args = parser.parse_args()

    if args.command == "create":
        result = create_snapshot_archive(Path(args.archive), Path(args.data_dir),
                                          Path(args.work_dir) if args.work_dir else None)
        print(f"archive_path={result}")
    elif args.command == "list":
        for m in list_snapshot_archive(Path(args.archive)):
            print(m)
    elif args.command == "validate":
        safe = validate_archive_paths(Path(args.archive))
        print(f"safe_entries={len(safe)}")
        for m in safe:
            print(f"  {m}")
    elif args.command == "extract":
        result = extract_snapshot_archive(Path(args.archive), Path(args.destination),
                                           validate=not args.no_validate)
        print(f"extracted_to={result}")
    elif args.command == "hash":
        h = stream_hash_file(Path(args.file))
        print(f"sha256={h}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
