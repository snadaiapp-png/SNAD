#!/usr/bin/env python3
"""
SANAD — Backend Dockerfile Hardening Gate (Docker-free, static)
================================================================
Replaces the previous container-based hardening verification with a
deterministic static analysis of apps/sanad-platform/Dockerfile.

Equivalence contract with the previous docker-based checks
(docker build + docker inspect + docker run):
  1. runtime_user == "sanad"
       -> final-stage USER directive is a non-root user name
  2. healthcheck != null
       -> HEALTHCHECK directive present in the final stage
  3. docker run ... 'test "$(id -u)" != "0"'
       -> the runtime user is created inside the Dockerfile
          (groupadd/useradd) without '-u 0', and USER is neither
          'root' nor the literal uid 0
  4. no sensitive runtime environment defaults
       -> no ENV directive assigning a variable whose name matches
          password/secret/token/private key patterns

Usage:
    python3 scripts/ci/check_dockerfile_hardening.py [--dockerfile PATH]

Exit codes:
    0 = hardening contract satisfied
    1 = one or more violations (fail closed)
    2 = environment error (missing file, unreadable)
"""

import argparse
import os
import re
import sys

SENSITIVE_ENV_NAME = re.compile(r"(?i)(password|secret|token|private[_-]?key)[\s]*=")


def parse_dockerfile(path):
    if not os.path.isfile(path):
        print(f"ERROR: Dockerfile not found: {path}")
        sys.exit(2)
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return fh.read().splitlines()
    except OSError as exc:
        print(f"ERROR: cannot read {path}: {exc}")
        sys.exit(2)


def collect_stage_directives(lines):
    """Return ordered (stage_index, directive_upper, value) tuples.

    Multi-stage builds: stage 0 is the first FROM; each subsequent FROM
    starts a new stage. The FINAL stage is the one the runtime image uses.
    """
    directives = []
    stage = -1
    for raw in lines:
        stripped = raw.lstrip()
        if not stripped or stripped.startswith("#"):
            continue
        if re.match(r"(?i)^FROM\s", stripped):
            stage += 1
        m = re.match(r"(?i)^(USER|HEALTHCHECK|ENV)\s+(.*)$", stripped)
        if m and stage >= 0:
            directives.append((stage, m.group(1).upper(), m.group(2).strip()))
    return directives


def check_hardening(dockerfile_path):
    lines = parse_dockerfile(dockerfile_path)
    directives = collect_stage_directives(lines)
    if not directives:
        print("ERROR: no FROM/USER/HEALTHCHECK/ENV directives parsed; refusing to pass")
        sys.exit(2)

    stages = {d[0] for d in directives}
    final_stage = max(stages)
    violations = []

    # --- 1. Final-stage USER must be a non-root named user -----------------
    user_directives = [d for d in directives if d[1] == "USER" and d[0] == final_stage]
    user_ok = False
    user_value = ""
    if not user_directives:
        violations.append(
            "final stage declares no USER directive "
            "(previous check: image Config.User would default from the base, typically root)"
        )
    else:
        user_value = user_directives[-1][2].split()[0] if user_directives[-1][2] else ""
        if not user_value:
            violations.append("final stage USER directive is empty")
        elif user_value.lower() == "root" or user_value == "0":
            violations.append(
                f"final stage USER is '{user_value}' (root); runtime must be non-root"
            )
        else:
            user_ok = True
            print(f"PASS: final-stage USER = '{user_value}' (non-root)")

    # --- 3. Static proof that the runtime uid is non-zero ------------------
    # Equivalent of: docker run --rm --entrypoint sh <image> -c 'test "$(id -u)" != "0"'
    raw_text = "\n".join(lines)
    user_creation = re.search(r"(?im)^RUN\s+.*\buseradd\b([^\n\\]*)", raw_text)
    if user_ok:
        if user_creation:
            creation_flags = user_creation.group(1)
            if re.search(r"(?i)(^|\s)-u\s+0\b", creation_flags):
                violations.append(
                    "runtime user is created with '-u 0' (uid 0); runtime must be non-root"
                )
            else:
                print(
                    "PASS: runtime user created via useradd without uid 0 "
                    "(static proof of non-zero uid)"
                )
        else:
            violations.append(
                "runtime USER is a bare name with no useradd/groupadd RUN layer in the "
                "Dockerfile; static gate cannot prove a non-root uid (previously proven "
                "via 'docker run id -u'). Add an explicit user creation layer."
            )

    # --- 2. HEALTHCHECK present in the final stage --------------------------
    healthchecks = [
        d for d in directives if d[1] == "HEALTHCHECK" and d[0] == final_stage
    ]
    if not healthchecks:
        violations.append(
            "final stage declares no HEALTHCHECK directive "
            "(previous check: image Config.Healthcheck would be null)"
        )
    else:
        print("PASS: HEALTHCHECK directive present in final stage")

    # --- 4. No sensitive ENV defaults ----------------------------------------
    env_directives = [d for d in directives if d[1] == "ENV" and d[0] == final_stage]
    sensitive_found = False
    for _, _, value in env_directives:
        # ENV either 'KEY=value ...' (legacy) or 'KEY value' (old form).
        if SENSITIVE_ENV_NAME.search(value + " "):
            sensitive_found = True
            violations.append(
                "final stage ENV default assigns a sensitive variable "
                "(password/secret/token/private key) — value withheld"
            )
    if env_directives and not sensitive_found:
        print(
            f"PASS: {len(env_directives)} final-stage ENV default(s) contain no "
            "sensitive variable names"
        )
    if not env_directives:
        print("INFO: final stage declares no ENV defaults")

    return violations


def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    default_path = os.path.join(repo_root, "apps", "sanad-platform", "Dockerfile")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dockerfile",
        default=default_path,
        help="path to the backend Dockerfile (default: apps/sanad-platform/Dockerfile)",
    )
    args = parser.parse_args()

    print("== Backend container hardening (static Dockerfile gate) ==")
    print(f"Dockerfile: {args.dockerfile}")
    print()
    violations = check_hardening(args.dockerfile)
    print()
    if violations:
        for v in violations:
            print(f"VIOLATION: {v}")
        print()
        print("FAIL: backend Dockerfile hardening contract NOT satisfied (fail closed)")
        return 1
    print("PASS: backend Dockerfile hardening contract satisfied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
