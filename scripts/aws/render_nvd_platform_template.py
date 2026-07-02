#!/usr/bin/env python3
from __future__ import annotations

import argparse
import pathlib


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()

    text = args.source.read_text(encoding="utf-8")
    rendered = text.replace("        - TagKey: Project\n          TagValue: SNAD", "        - Key: Project\n          Value: SNAD")
    rendered = rendered.replace("        - TagKey: Purpose\n          TagValue: NVDSnapshotEncryption", "        - Key: Purpose\n          Value: NVDSnapshotEncryption")
    rendered = rendered.replace("        - TagKey: ManagedBy\n          TagValue: CloudFormation", "        - Key: ManagedBy\n          Value: CloudFormation")

    if "TagKey:" in rendered or "TagValue:" in rendered:
        raise SystemExit("unconverted KMS tag keys remain in rendered template")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
