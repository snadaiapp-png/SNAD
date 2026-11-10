#!/usr/bin/env python3
"""Regression tests for scripts/ci/check_dockerfile_hardening.py.

The static Dockerfile hardening gate replaces the previous Docker-based
container hardening verification (docker build + inspect + run). These
tests prove the gate fails closed on every violation class that the
container-based check previously caught.
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "ci"))

import check_dockerfile_hardening as gate  # noqa: E402

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
REAL_DOCKERFILE = os.path.join(REPO_ROOT, "apps", "sanad-platform", "Dockerfile")


def write_tmp(lines):
    tmp = tempfile.NamedTemporaryFile("w", suffix="Dockerfile", delete=False)
    tmp.write("\n".join(lines) + "\n")
    tmp.close()
    return tmp.name


GOOD_MULTI_STAGE = [
    "FROM maven:3.9-eclipse-temurin-21 AS builder",
    "WORKDIR /workspace",
    "RUN mvn -B package",
    "FROM eclipse-temurin:21-jre-jammy",
    "RUN groupadd -r sanad && useradd -r -g sanad sanad",
    "WORKDIR /app",
    "COPY --from=builder /workspace/target/app.jar app.jar",
    "ENV JAVA_OPTS=-Xmx160m",
    "USER sanad",
    "EXPOSE 8080",
    "HEALTHCHECK --interval=15s --timeout=10s CMD curl -f http://localhost:8080/actuator/health || exit 1",
    "ENTRYPOINT [\"sh\", \"-c\", \"exec java $JAVA_OPTS -jar app.jar\"]",
]


class DockerfileHardeningGateTests(unittest.TestCase):
    def tearDown(self):
        for name in list(vars(self)):
            if name.startswith("_tmp_"):
                try:
                    os.unlink(getattr(self, name))
                except OSError:
                    pass

    def tmp(self, lines):
        path = write_tmp(lines)
        setattr(self, "_tmp_" + str(id(path)), path)
        return path

    def test_real_production_dockerfile_passes(self):
        violations = gate.check_hardening(REAL_DOCKERFILE)
        self.assertEqual(violations, [], f"unexpected violations: {violations}")

    def test_good_multi_stage_passes(self):
        self.assertEqual(self.tmp(GOOD_MULTI_STAGE) and gate.check_hardening(self.tmp(GOOD_MULTI_STAGE)), [])

    def test_user_root_fails(self):
        bad = [l for l in GOOD_MULTI_STAGE if not l.startswith("USER ")] + ["USER root"]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_user_uid_zero_fails(self):
        bad = [l for l in GOOD_MULTI_STAGE if not l.startswith("USER ")] + ["USER 0"]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_missing_user_fails(self):
        bad = [l for l in GOOD_MULTI_STAGE if not l.startswith("USER ")]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_missing_healthcheck_fails(self):
        bad = [l for l in GOOD_MULTI_STAGE if not l.startswith("HEALTHCHECK")]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_sensitive_env_default_fails(self):
        bad = GOOD_MULTI_STAGE[:-1] + ["ENV JWT_SECRET=hardcoded-value", GOOD_MULTI_STAGE[-1]]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_useradd_uid_zero_fails(self):
        bad = [l.replace("useradd -r -g sanad sanad", "useradd -r -g sanad -u 0 sanad")
               for l in GOOD_MULTI_STAGE]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_bare_user_without_creation_layer_fails(self):
        bad = [l for l in GOOD_MULTI_STAGE if "useradd" not in l and "groupadd" not in l]
        self.assertNotEqual(gate.check_hardening(self.tmp(bad)), [])

    def test_final_stage_only_not_builder(self):
        # Builder stage USER root must NOT matter; final stage USER sanad must win.
        staged = list(GOOD_MULTI_STAGE)
        staged.insert(2, "USER root")
        self.assertEqual(gate.check_hardening(self.tmp(staged)), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
