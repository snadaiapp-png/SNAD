#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "apps/sanad-platform/src/main/java"
WEB = JAVA / "com/sanad/platform/crm/web"
LEGACY = JAVA / "com/sanad/platform/crm/legacy/infrastructure"
MARKER = ROOT / ".crm-004-extended-transformed"

if MARKER.exists():
    print("CRM-004 extended transformation already applied")
    raise SystemExit(0)

LEGACY.mkdir(parents=True, exist_ok=True)

# Split package-private request records into public source files so the relocated
# compatibility infrastructure can accept the existing controller DTOs.
models = WEB / "CrmModels.java"
text = models.read_text(encoding="utf-8")
imports = "\n".join(line for line in text.splitlines() if line.startswith("import "))
pattern = re.compile(r"(?ms)^record\s+(\w+)\s*\((.*?)\)\s*\{\s*\}\s*")
records = list(pattern.finditer(text))
if not records:
    raise RuntimeError("No CRM request records found")
for match in records:
    name, body = match.group(1), match.group(2)
    target = WEB / f"{name}.java"
    target.write_text(
        "package com.sanad.platform.crm.web;\n\n" + imports +
        f"\n\npublic record {name}(\n{body.strip()}\n) {{ }}\n",
        encoding="utf-8",
    )
models.write_text(
    "package com.sanad.platform.crm.web;\n\n"
    "/** Request DTOs were split into public records by CRM-004 final decomposition. */\n"
    "final class CrmModels { private CrmModels() {} }\n",
    encoding="utf-8",
)

def expose_methods(source: str) -> str:
    # Make package-private controller-facing methods public after relocation.
    method = re.compile(
        r"(?m)^(    )(?!private\s|public\s|protected\s|static\s|class\s|record\s|interface\s)"
        r"(?=(?:Map<|List<|Set<|String\s|void\s|boolean\s|long\s|int\s|UUID\s|byte\[\]\s)\w+\s*\()"
    )
    return method.sub(r"\1public ", source)

def relocate(old_name: str, new_name: str):
    old = WEB / f"{old_name}.java"
    source = old.read_text(encoding="utf-8")
    source = source.replace(
        "package com.sanad.platform.crm.web;",
        "package com.sanad.platform.crm.legacy.infrastructure;\n\nimport com.sanad.platform.crm.web.*;",
        1,
    )
    source = source.replace(old_name, new_name)
    source = re.sub(rf"(?m)^(class|public class)\s+{re.escape(new_name)}\b", f"public class {new_name}", source)
    source = re.sub(rf"(?m)^(    ){re.escape(new_name)}\(", rf"\1public {new_name}(", source)
    source = expose_methods(source)
    target = LEGACY / f"{new_name}.java"
    target.write_text(source, encoding="utf-8")
    old.unlink()

relocate("CrmExtendedService", "LegacyCrmInfrastructureService")
relocate("CrmV2AtomicMutationService", "CrmV2AtomicMutationInfrastructureService")

# Update references across main and test sources.
for base in [JAVA, ROOT / "apps/sanad-platform/src/test/java"]:
    for path in base.rglob("*.java"):
        if path.is_relative_to(LEGACY):
            continue
        source = path.read_text(encoding="utf-8")
        original = source
        replacements = {
            "CrmExtendedService": "LegacyCrmInfrastructureService",
            "CrmV2AtomicMutationService": "CrmV2AtomicMutationInfrastructureService",
        }
        for old, new in replacements.items():
            source = source.replace(old, new)
        imports_to_add = []
        if "LegacyCrmInfrastructureService" in source and "package com.sanad.platform.crm.legacy.infrastructure;" not in source:
            imports_to_add.append("import com.sanad.platform.crm.legacy.infrastructure.LegacyCrmInfrastructureService;")
        if "CrmV2AtomicMutationInfrastructureService" in source and "package com.sanad.platform.crm.legacy.infrastructure;" not in source:
            imports_to_add.append("import com.sanad.platform.crm.legacy.infrastructure.CrmV2AtomicMutationInfrastructureService;")
        if imports_to_add:
            package_end = source.find(";", source.find("package ")) + 1
            source = source[:package_end] + "\n\n" + "\n".join(imports_to_add) + source[package_end:]
        if source != original:
            path.write_text(source, encoding="utf-8")

MARKER.write_text("transformed\n", encoding="utf-8")
print(f"Split {len(records)} DTO records and relocated legacy JDBC services")
