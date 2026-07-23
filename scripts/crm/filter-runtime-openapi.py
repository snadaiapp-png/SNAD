#!/usr/bin/env python3
"""Create the committed CRM-only OpenAPI contract from the platform runtime spec.

The platform exposes one aggregate OpenAPI document. This tool selects only
/api/v2/crm operations, strips that prefix from committed paths, and retains
only transitively referenced OpenAPI components. The output is deterministic
and suitable for generated TypeScript contracts and semantic drift checks.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
from typing import Any, Iterable

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
COMPONENT_PREFIX = "#/components/"


def walk_strings(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        for child in value.values():
            yield from walk_strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_strings(child)
    elif isinstance(value, str):
        yield value


def collect_security_names(value: Any) -> set[str]:
    names: set[str] = set()
    if isinstance(value, dict):
        security = value.get("security")
        if isinstance(security, list):
            for requirement in security:
                if isinstance(requirement, dict):
                    names.update(str(name) for name in requirement)
        for child in value.values():
            names.update(collect_security_names(child))
    elif isinstance(value, list):
        for child in value:
            names.update(collect_security_names(child))
    return names


def parse_component_ref(value: str) -> tuple[str, str] | None:
    if not value.startswith(COMPONENT_PREFIX):
        return None
    remainder = value[len(COMPONENT_PREFIX):]
    parts = remainder.split("/", 1)
    if len(parts) != 2 or not all(parts):
        return None
    return parts[0], parts[1]


def select_components(source: dict[str, Any], roots: list[Any]) -> dict[str, Any]:
    source_components = source.get("components", {})
    selected: dict[str, dict[str, Any]] = {}
    pending: list[tuple[str, str]] = []
    seen: set[tuple[str, str]] = set()

    def enqueue_from(value: Any) -> None:
        for text in walk_strings(value):
            parsed = parse_component_ref(text)
            if parsed and parsed not in seen:
                pending.append(parsed)

    for root in roots:
        enqueue_from(root)

    security_names: set[str] = set()
    for root in roots:
        security_names.update(collect_security_names(root))
    for name in sorted(security_names):
        if name in source_components.get("securitySchemes", {}):
            pending.append(("securitySchemes", name))

    while pending:
        section, name = pending.pop()
        key = (section, name)
        if key in seen:
            continue
        seen.add(key)
        section_values = source_components.get(section, {})
        if name not in section_values:
            raise ValueError(f"Referenced component does not exist: {section}/{name}")
        component = copy.deepcopy(section_values[name])
        selected.setdefault(section, {})[name] = component
        enqueue_from(component)
        for security_name in collect_security_names(component):
            if security_name in source_components.get("securitySchemes", {}):
                pending.append(("securitySchemes", security_name))

    return {
        section: {name: values[name] for name in sorted(values)}
        for section, values in sorted(selected.items())
    }


def build_crm_spec(source: dict[str, Any], prefix: str) -> dict[str, Any]:
    if not prefix.startswith("/") or prefix.endswith("/"):
        raise ValueError("prefix must start with '/' and must not end with '/'")

    selected_paths: dict[str, Any] = {}
    for runtime_path, item in source.get("paths", {}).items():
        if runtime_path == prefix or runtime_path.startswith(prefix + "/"):
            committed_path = runtime_path[len(prefix):] or "/"
            if committed_path in selected_paths:
                raise ValueError(f"Duplicate committed path after prefix removal: {committed_path}")
            selected_paths[committed_path] = copy.deepcopy(item)

    if not selected_paths:
        raise ValueError(f"No OpenAPI paths found under {prefix}")

    roots: list[Any] = list(selected_paths.values())
    if source.get("security") is not None:
        roots.append(source["security"])
    components = select_components(source, roots)

    used_tags = {
        tag
        for item in selected_paths.values()
        if isinstance(item, dict)
        for method, operation in item.items()
        if method in HTTP_METHODS and isinstance(operation, dict)
        for tag in operation.get("tags", [])
    }
    tags = [copy.deepcopy(tag) for tag in source.get("tags", []) if tag.get("name") in used_tags]

    info = copy.deepcopy(source.get("info", {}))
    info["title"] = "SANAD CRM API"
    info["description"] = "CRM-only contract generated from the SANAD platform runtime OpenAPI document."

    result: dict[str, Any] = {
        "openapi": source["openapi"],
        "info": info,
        "servers": [{"url": prefix}],
        "paths": {path: selected_paths[path] for path in sorted(selected_paths)},
    }
    if tags:
        result["tags"] = tags
    if components:
        result["components"] = components
    if source.get("security") is not None:
        result["security"] = copy.deepcopy(source["security"])
    return result


def count_operations(spec: dict[str, Any]) -> int:
    return sum(
        1
        for item in spec.get("paths", {}).values()
        if isinstance(item, dict)
        for method in item
        if method in HTTP_METHODS
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--prefix", default="/api/v2/crm")
    parser.add_argument("--expected-paths", type=int)
    parser.add_argument("--expected-operations", type=int)
    args = parser.parse_args()

    source = json.loads(args.input.read_text(encoding="utf-8"))
    filtered = build_crm_spec(source, args.prefix)
    path_count = len(filtered["paths"])
    operation_count = count_operations(filtered)

    # Preserve the exact runtime evidence even when count validation fails.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(filtered, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    if args.expected_paths is not None and path_count != args.expected_paths:
        raise SystemExit(f"Expected {args.expected_paths} CRM paths, got {path_count}")
    if args.expected_operations is not None and operation_count != args.expected_operations:
        raise SystemExit(
            f"Expected {args.expected_operations} CRM operations, got {operation_count}"
        )

    print(
        f"CRM OpenAPI generated: {path_count} paths, {operation_count} operations, "
        f"{sum(len(values) for values in filtered.get('components', {}).values())} components"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
