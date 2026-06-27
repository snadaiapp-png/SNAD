#!/usr/bin/env python3
"""
SANAD — NVD Bulk Feed META Parser
==================================
EXEC-PROMPT-010R12L Section 10/11 — strict parser for NVD META files.

NVD META files look like:
  lastModifiedDate:2024-01-15T05:00:14.001-05:00
  size:1234567
  zipSize:987654
  gzSize:987654
  sha256:abcdef1234567890...
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional

META_KEY_PATTERN = re.compile(r'^(\w+):\s*(.*)$')


@dataclass(frozen=True)
class NvdFeedMeta:
    """Parsed NVD META file."""
    last_modified_date: str
    size: int
    zip_size: int
    gz_size: int
    sha256: str
    raw_content: str

    def to_dict(self) -> dict:
        return {
            "last_modified_date": self.last_modified_date,
            "size": self.size,
            "zip_size": self.zip_size,
            "gz_size": self.gz_size,
            "sha256": self.sha256,
        }


class MetaParseError(ValueError):
    """Raised when META file parsing fails."""


def parse_meta(content: str) -> NvdFeedMeta:
    """Parse NVD META file content strictly.

    Raises MetaParseError on any violation.
    """
    if not content or not content.strip():
        raise MetaParseError("META content is empty")

    fields: dict[str, str] = {}
    seen_keys: set[str] = set()

    for line_num, line in enumerate(content.strip().split('\n'), 1):
        line = line.strip()
        if not line:
            continue
        m = META_KEY_PATTERN.match(line)
        if not m:
            raise MetaParseError(f"Line {line_num}: invalid format (expected key:value): {line[:80]}")
        key = m.group(1).strip()
        value = m.group(2).strip()
        if key in seen_keys:
            raise MetaParseError(f"Line {line_num}: duplicate key '{key}'")
        seen_keys.add(key)
        fields[key] = value

    # Required fields
    required = ("lastModifiedDate", "size", "zipSize", "gzSize", "sha256")
    for req in required:
        if req not in fields:
            raise MetaParseError(f"Missing required field: {req}")

    # Validate sha256 (64 hex chars)
    sha = fields["sha256"]
    if not re.match(r'^[a-fA-F0-9]{64}$', sha):
        raise MetaParseError(f"Invalid sha256 digest (expected 64 hex chars): {sha[:20]}...")

    # Validate numeric fields
    try:
        size = int(fields["size"])
        if size < 0:
            raise MetaParseError(f"size must be non-negative, got: {size}")
    except ValueError:
        raise MetaParseError(f"size is not an integer: {fields['size']}")

    try:
        zip_size = int(fields["zipSize"])
        if zip_size < 0:
            raise MetaParseError(f"zipSize must be non-negative, got: {zip_size}")
    except ValueError:
        raise MetaParseError(f"zipSize is not an integer: {fields['zipSize']}")

    try:
        gz_size = int(fields["gzSize"])
        if gz_size < 0:
            raise MetaParseError(f"gzSize must be non-negative, got: {gz_size}")
    except ValueError:
        raise MetaParseError(f"gzSize is not an integer: {fields['gzSize']}")

    # Validate timestamp format (basic check)
    ts = fields["lastModifiedDate"]
    if not re.match(r'^\d{4}-\d{2}-\d{2}T', ts):
        raise MetaParseError(f"Invalid lastModifiedDate format: {ts}")

    return NvdFeedMeta(
        last_modified_date=ts,
        size=size,
        zip_size=zip_size,
        gz_size=gz_size,
        sha256=sha.lower(),
        raw_content=content,
    )
