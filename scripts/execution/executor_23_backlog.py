#!/usr/bin/env python3
"""Load the reviewed Executor #23 source parts and execute the generator."""
from pathlib import Path

path = Path(__file__).resolve()
parts = sorted(path.parent.glob(path.name + ".part*"))
if not parts:
    raise SystemExit("Executor #23 source parts are missing")

source = "".join(part.read_text(encoding="utf-8") for part in parts)
namespace = {
    "__name__": "__main__",
    "__file__": str(path),
    "__package__": None,
}
exec(compile(source, str(path), "exec"), namespace)
