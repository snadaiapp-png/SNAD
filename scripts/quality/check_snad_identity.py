#!/usr/bin/env python3
"""Validate the active SNAD identity and typography contract."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOKENS = ROOT / "apps/web/app/snad-tokens.css"
LAYOUT = ROOT / "apps/web/app/layout.tsx"

REQUIRED = {
    "--snad-brand-primary": "#003b39",
    "--snad-brand-gold": "#d4af37",
    "--snad-font-arabic": "Noto Sans Arabic",
    "--snad-font-latin": "Noto Sans",
}


def main() -> int:
    errors: list[str] = []
    if not TOKENS.is_file():
        errors.append("Missing apps/web/app/snad-tokens.css")
    else:
        content = TOKENS.read_text(encoding="utf-8")
        lowered = content.lower()
        for token, expected in REQUIRED.items():
            if token.lower() not in lowered or expected.lower() not in lowered:
                errors.append(f"Missing required identity contract: {token} -> {expected}")
        if '[data-theme="dark"]' not in content:
            errors.append("Dark theme mapping is required")

    if not LAYOUT.is_file():
        errors.append("Missing apps/web/app/layout.tsx")
    else:
        layout = LAYOUT.read_text(encoding="utf-8")
        for required in ("Noto_Sans_Arabic", "Noto_Sans", "--font-snad-arabic", "--font-snad-latin"):
            if required not in layout:
                errors.append(f"Missing font loader contract: {required}")
        if re.search(r'\bSANAD\b', layout):
            errors.append("Legacy SANAD label is forbidden in active layout metadata")

    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1
    print("SNAD identity validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
