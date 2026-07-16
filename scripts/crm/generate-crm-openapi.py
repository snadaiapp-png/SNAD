#!/usr/bin/env python3
"""Merge new runtime CRM operations into the previously governed CRM contract.

The baseline contract contains deliberate governance enrichments that Springdoc does
not infer reliably: reusable pagination parameters, standard error schemas,
security schemes, precise create response codes, ETag headers and concurrency
responses. This generator therefore never replaces existing governed operations.
It adds runtime-only paths and the component graph required by those paths.
"""

from __future__ import annotations

import copy
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


def relative_runtime_paths(runtime: dict[str, Any]) -> dict[str, Any]:
    paths: dict[str, Any] = {}
    for full_path, item in runtime.get("paths", {}).items():
        if not full_path.startswith(CRM_PREFIX):
            continue
        relative = full_path.removeprefix(CRM_PREFIX) or "/"
        paths[relative] = item
    if not paths:
        raise RuntimeError("Runtime OpenAPI contains no /api/v2/crm operations")
    return paths


def merge_contract(
    baseline: dict[str, Any], runtime: dict[str, Any]
) -> tuple[dict[str, Any], list[str]]:
    governed = copy.deepcopy(baseline)
    runtime_paths = relative_runtime_paths(runtime)
    governed_paths = governed.setdefault("paths", {})

    new_paths = sorted(path for path in runtime_paths if path not in governed_paths)
    for path in new_paths:
        governed_paths[path] = copy.deepcopy(runtime_paths[path])

    # Existing governed operations are intentionally immutable here. A runtime
    # change to an existing operation is evaluated by the semantic drift gate,
    # not silently accepted by this generator.
    components = governed.setdefault("components", {})
    pending = deque(sorted(set(component_refs({path: runtime_paths[path] for path in new_paths}))))
    visited: set[str] = set()
    while pending:
        ref = pending.popleft()
        if ref in visited:
            continue
        visited.add(ref)
        section, name, runtime_value = lookup_component(runtime, ref)
        section_values = components.setdefault(section, {})
        if name not in section_values:
            section_values[name] = copy.deepcopy(runtime_value)
        selected = section_values[name]
        for nested in component_refs(selected):
            if nested not in visited:
                pending.append(nested)

    # Runtime operation tags may not be declared at the OpenAPI root. Add only
    # missing tag declarations while preserving the curated baseline order.
    tags = governed.setdefault("tags", [])
    declared = {tag.get("name") for tag in tags if isinstance(tag, dict)}
    used = {
        tag
        for path in new_paths
        for method, operation in runtime_paths[path].items()
        if method in HTTP_METHODS and isinstance(operation, dict)
        for tag in operation.get("tags", [])
    }
    for tag in sorted(used - declared):
        tags.append({"name": tag})

    info = governed.setdefault("info", {})
    info["title"] = "SNAD CRM API"
    info["version"] = "2.1.0"
    info["description"] = (
        "Governed CRM v2 API contract extended by EXEC-PROMPT-CRM-005 "
        "Enterprise Account and Customer Master. Existing CRM-G2 operations "
        "retain their reviewed security, pagination, response and concurrency semantics."
    )

    governed["paths"] = dict(sorted(governed_paths.items()))
    governed["components"] = {
        section: dict(sorted(values.items()))
        for section, values in sorted(components.items())
    }
    return governed, new_paths


def operation_count(spec: dict[str, Any]) -> int:
    return sum(
        1
        for item in spec.get("paths", {}).values()
        for method in item
        if method in HTTP_METHODS
    )


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(
            "Usage: generate-crm-openapi.py "
            "<baseline-governed.json> <runtime-openapi.json> <output-crm-openapi.json>"
        )
    baseline_path = Path(sys.argv[1])
    runtime_path = Path(sys.argv[2])
    output_path = Path(sys.argv[3])
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    governed, new_paths = merge_contract(baseline, runtime)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(governed, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Generated {output_path}: {len(governed['paths'])} paths, "
        f"{operation_count(governed)} operations, "
        f"{sum(len(v) for v in governed['components'].values())} components; "
        f"added paths={new_paths}"
    )


if __name__ == "__main__":
    main()
