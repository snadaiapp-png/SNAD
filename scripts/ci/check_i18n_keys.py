#!/usr/bin/env python3
"""
SNAD i18n Key Parity Checker — Fail-Closed

Ensures every translation key present in the Arabic dictionary is also present
in the English dictionary (and vice versa). Missing keys cause CI to fail.

Also detects hardcoded user-facing strings in source files is a future
enhancement — for now this script focuses on key parity, which is the most
critical invariant.

Usage:
    python3 scripts/ci/check_i18n_keys.py

Exit 0 = all keys present in both dictionaries.
Exit 1 = at least one key is missing in one of the dictionaries.
"""
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
AR_PATH = REPO_ROOT / "apps" / "web" / "lib" / "i18n" / "locales" / "ar.ts"
EN_PATH = REPO_ROOT / "apps" / "web" / "lib" / "i18n" / "locales" / "en.ts"

# Matches:   "some.key": "value",
KEY_PATTERN = re.compile(r'^\s*"([a-zA-Z0-9_.]+)"\s*:', re.MULTILINE)


def extract_keys(path: Path) -> set:
    """Extract translation keys from a TypeScript dictionary file."""
    if not path.exists():
        print(f"ERROR: dictionary file not found: {path}", file=sys.stderr)
        sys.exit(2)
    text = path.read_text(encoding="utf-8")
    return set(KEY_PATTERN.findall(text))


def main() -> int:
    ar_keys = extract_keys(AR_PATH)
    en_keys = extract_keys(EN_PATH)

    missing_in_en = ar_keys - en_keys
    missing_in_ar = en_keys - ar_keys

    print(f"Arabic keys : {len(ar_keys)}")
    print(f"English keys: {len(en_keys)}")

    if missing_in_en:
        print(f"\nFAIL: {len(missing_in_en)} key(s) present in ar.ts but MISSING in en.ts:")
        for key in sorted(missing_in_en):
            print(f"  - {key}")

    if missing_in_ar:
        print(f"\nFAIL: {len(missing_in_ar)} key(s) present in en.ts but MISSING in ar.ts:")
        for key in sorted(missing_in_ar):
            print(f"  - {key}")

    if missing_in_en or missing_in_ar:
        print("\nResult: FAIL — translation key parity violated.")
        return 1

    if ar_keys != en_keys:
        print("FAIL: key sets differ but no symmetric difference detected (unexpected).")
        return 1

    print(f"\nResult: PASS — all {len(ar_keys)} keys present in both ar.ts and en.ts.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
