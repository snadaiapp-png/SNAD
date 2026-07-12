#!/usr/bin/env python3
from __future__ import annotations
import re
import sys
import uuid
from pathlib import Path
UUID_LITERAL = re.compile(
    r"'([A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-"
    r"[A-Za-z0-9]{4}-[A-Za-z0-9]{12})'"
)
def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {Path(sys.argv[0]).name} <seed.sql>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    candidates = sorted(set(UUID_LITERAL.findall(path.read_text(encoding="utf-8"))))
    invalid: list[str] = []
    for candidate in candidates:
        try:
            uuid.UUID(candidate)
        except ValueError:
            invalid.append(candidate)
    if invalid:
        print("Invalid UUID literals:", file=sys.stderr)
        for value in invalid:
            print(f"  {value}", file=sys.stderr)
        return 1
    print(f"Validated {len(candidates)} unique UUID literals")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
