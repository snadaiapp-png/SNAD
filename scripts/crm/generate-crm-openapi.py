#!/usr/bin/env python3
"""Extract the governed CRM v2 contract from the platform runtime OpenAPI document."""

from __future__ import annotations

import json
import sys
from collections import deque
from pathlib import Path
from typing import Any, Iterable

CRM_PREFIX = "/api/v2/crm"
HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}


def component_refs(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        ref = value.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/"):
            yield ref
        for child in value.values():
            yield from component_refs(child)
    elif isinstance(value, list):
        for child in value:
            yield from component_refs(child)


def lookup_component(spec: dict[str, Any], ref: str) -> tuple[str, str, Any]:
    parts = ref.removeprefix("#/").split("/")
    if len(parts) != 3 or parts[0] != "components":
        raise ValueError(f"Unsupported component reference: {ref}")
    section, name = parts[1], parts[2]
    value = spec.get("components", {}).get(section, {}).get(name)
    if value is None:
        raise KeyError(f"Missing component referenced by runtime OpenAPI: {ref}")
    return section, name, value


def extract(runtime: dict[str, Any]) -> dict[str, Any]:
    crm_paths: dict[str, Any] = {}
    for full_path, item in runtime.get("paths", {}).items():
        if not full_path.startswith(CRM_PREFIX):
            continue
        relative = full_path.removeprefix(CRM_PREFIX) or "/"
        crm_paths[relative] = item

    if not crm_paths:
        raise RuntimeError("Runtime OpenAPI contains no /api/v2/crm operations")

    filtered_components: dict[str, dict[str, Any]] = {}
    pending = deque(sorted(set(component_refs(crm_paths))))
    visited: set[str] = set()
    while pending:
        ref = pending.popleft()
        if ref in visited:
            continue
        visited.add(ref)
        section, name, value = lookup_component(runtime, ref)
        filtered_components.setdefault(section, {})[name] = value
        for nested in component_refs(value):
            if nested not in visited:
                pending.append(nested)

    # Keep security schemes because operation-level security references them by name,
    # not by $ref. No non-CRM schemas are pulled in by this rule.
    security_schemes = runtime.get("components", {}).get("securitySchemes")
    if security_schemes:
        filtered_components["securitySchemes"] = security_schemes

    tags_used = {
        tag
        for item in crm_paths.values()
        for method, operation in item.items()
        if method in HTTP_METHODS and isinstance(operation, dict)
        for tag in operation.get("tags", [])
    }
    runtime_tags = runtime.get("tags", [])
    tags = [tag for tag in runtime_tags if tag.get("name") in tags_used]

    info = dict(runtime.get("info", {}))
    info.update(
        {
            "title": "SNAD CRM API",
            "version": "2.1.0",
            "description": (
                "Runtime-derived governed CRM v2 API contract including "
                "EXEC-PROMPT-CRM-005 Enterprise Account and Customer Master."
            ),
        }
    )

    return {
        "openapi": runtime.get("openapi", "3.1.0"),
        "info": info,
        "servers": [{"url": CRM_PREFIX, "description": "CRM v2 API root"}],
        "tags": tags,
        "paths": dict(sorted(crm_paths.items())),
        "components": {
            section: dict(sorted(values.items()))
            for section, values in sorted(filtered_components.items())
        },
    }


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: generate-crm-openapi.py <runtime-openapi.json> <output-crm-openapi.json>"
        )
    runtime_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    governed = extract(runtime)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(governed, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    operations = sum(
        1
        for item in governed["paths"].values()
        for method in item
        if method in HTTP_METHODS
    )
    print(
        f"Generated {output_path}: {len(governed['paths'])} paths, "
        f"{operations} operations, "
        f"{sum(len(v) for v in governed['components'].values())} components"
    )


if __name__ == "__main__":
    main()
