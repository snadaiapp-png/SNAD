#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
for relative in [
    ".github/workflows/crm-authenticated-acceptance.yml",
    ".github/workflows/playwright-ci.yml",
]:
    path = root / relative
    text = path.read_text(encoding="utf-8")
    text = text.replace("  cancel-in-progress: false", "  cancel-in-progress: true", 1)
    path.write_text(text, encoding="utf-8")
Path(__file__).unlink()
