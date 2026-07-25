from pathlib import Path

import pytest

from scripts.security.nvd_datafeed_runtime_hardening import normalize_feed_metadata


def test_normalize_feed_metadata_creates_cache_and_meta(tmp_path: Path) -> None:
    payload = tmp_path / "nvdcve-modified.json.gz"
    payload.write_bytes(b"test-feed")

    normalize_feed_metadata(tmp_path)

    cache = (tmp_path / "cache.properties").read_text(encoding="utf-8")
    meta = (tmp_path / "nvdcve-modified.meta").read_text(encoding="utf-8")

    assert "lastModifiedDate.modified=" in cache
    assert "lastModifiedDate=" in meta
    assert "gzSize=9" in meta
    assert "sha256=" in meta


def test_normalize_feed_metadata_rejects_empty_feed(tmp_path: Path) -> None:
    with pytest.raises(RuntimeError, match="No NVD datafeed payloads found"):
        normalize_feed_metadata(tmp_path)


def test_normalize_feed_metadata_preserves_existing_meta(tmp_path: Path) -> None:
    payload = tmp_path / "nvdcve-recent.json.gz"
    payload.write_bytes(b"payload")
    meta = tmp_path / "nvdcve-recent.meta"
    meta.write_text("trusted=true\n", encoding="utf-8")

    normalize_feed_metadata(tmp_path)

    assert meta.read_text(encoding="utf-8") == "trusted=true\n"
