"""Regression tests for CI repository selection; no network or database required."""
import os
import http.server
import threading
import time
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/prepare-maven-central.sh"
ENDPOINTS = (
    "https://repo.maven.apache.org/maven2",
    "https://repo1.maven.org/maven2",
    "https://maven-central.storage-download.googleapis.com/maven2",
)
NS = {"s": "http://maven.apache.org/SETTINGS/1.2.0"}

class MavenCentralTransportTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="snad-maven-ci-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.home = self.root / "home"
        self.home.mkdir()
        self.runner = self.root / "runner"
        self.runner.mkdir()
        self.envfile = self.root / "github-env"
        self.envfile.touch()
        self.calls = self.root / "calls"
        self.calls.touch()
        self.write_executable("mvn", """#!/usr/bin/env bash
printf 'Apache Maven %s\\n' "${FAKE_MAVEN_VERSION:-3.9.9}"
""")
        self.write_executable("curl", """#!/usr/bin/env python3
import os, pathlib, sys
args = sys.argv[1:]
assert "--retry" in args and args[args.index("--retry") + 1] == "3"
assert "--retry-delay" not in args
assert "--retry-max-time" in args and args[args.index("--retry-max-time") + 1] == "90"
assert "--retry-all-errors" not in args
url = args[-1]
with open(os.environ["FAKE_CALLS"], "a") as fh:
    fh.write(url + "\\n")
i = len(pathlib.Path(os.environ["FAKE_CALLS"]).read_text().splitlines()) - 1
mode = os.environ["FAKE_MODES"].split(",")[i]
if mode == "429":
    sys.exit(22)
out = pathlib.Path(args[args.index("-o") + 1])
if mode == "invalid":
    out.write_text("<html>not a POM</html>")
else:
    version = "3.5.6" if mode == "ok" else "0.0.0"
    out.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>' + version + '</version></parent><artifactId>spring-boot-starter-parent</artifactId></project>')
""")
    def write_executable(self, name, content):
        p = self.bin / name
        p.write_text(content)
        p.chmod(0o755)

    def invoke(self, modes, version="3.9.9"):
        env = dict(os.environ, HOME=str(self.home), RUNNER_TEMP=str(self.runner),
                   GITHUB_ENV=str(self.envfile), FAKE_CALLS=str(self.calls),
                   FAKE_MODES=modes, FAKE_MAVEN_VERSION=version,
                   PATH=str(self.bin) + os.pathsep + os.environ["PATH"])
        return subprocess.run(["bash", str(SCRIPT)], env=env, capture_output=True,
                              text=True, timeout=15)

    def endpoints_called(self):
        return [u.split("/org/springframework/")[0] for u in self.calls.read_text().splitlines()]

    def assert_config(self, endpoint):
        settings = self.runner / "snad-ci-central-settings.xml"
        mirror = ET.parse(settings).find("s:mirrors/s:mirror", NS)
        self.assertEqual(mirror.findtext("s:id", namespaces=NS), "snad-ci-central-v1")
        self.assertEqual(mirror.findtext("s:mirrorOf", namespaces=NS), "central")
        self.assertEqual(mirror.findtext("s:url", namespaces=NS), endpoint)
        args = self.envfile.read_text()
        self.assertIn("--strict-checksums", args)
        self.assertIn(f"-Dmaven.repo.local={self.home}/.m2/snad-ci-central-v1", args)
        self.assertIn(f"MAVEN_CI_ENDPOINT={endpoint}", args)

    def test_primary(self):
        res = self.invoke("ok")
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertEqual(self.endpoints_called(), list(ENDPOINTS[:1]))
        self.assert_config(ENDPOINTS[0])

    def test_429_falls_back(self):
        res = self.invoke("429,ok")
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertEqual(self.endpoints_called(), list(ENDPOINTS[:2]))
        self.assert_config(ENDPOINTS[1])

    def test_invalid_content_and_wrong_version(self):
        res = self.invoke("invalid,wrong,ok")
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertEqual(self.endpoints_called(), list(ENDPOINTS))
        self.assert_config(ENDPOINTS[2])

    def test_all_endpoints_fail_closed(self):
        res = self.invoke("429,invalid,429")
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("MAVEN_CENTRAL_UNAVAILABLE", res.stdout)
        self.assertEqual(self.envfile.read_text(), "")
        self.assertFalse((self.runner / "snad-ci-central-settings.xml").exists())
        self.assertEqual(self.endpoints_called(), list(ENDPOINTS))

    def test_maven_version_fail_closed(self):
        res = self.invoke("ok", "3.8.8")
        self.assertNotEqual(res.returncode, 0)
        self.assertEqual(self.endpoints_called(), [])
        self.assertEqual(self.envfile.read_text(), "")


class CurlRetryIntegrationTest(unittest.TestCase):
    """Exercise real curl retries against a loopback HTTP server."""

    def setUp(self):
        if not shutil.which("curl"):
            self.skipTest("curl unavailable")
        self.failures = 0
        self.requests = []
        outer = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                outer.requests.append(time.monotonic())
                status = 429 if len(outer.requests) <= outer.failures else 200
                self.send_response(status)
                self.send_header("Content-Length", "2")
                self.end_headers()
                self.wfile.write(b"ok")

            def log_message(self, *args):
                pass

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.thread.join, 3)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def curl(self):
        return subprocess.run(
            ["curl", "--fail", "--silent", "--show-error", "--retry", "2",
             "--retry-max-time", "15", "--connect-timeout", "2", "--max-time", "3",
             f"http://127.0.0.1:{self.server.server_port}/probe"],
            capture_output=True, text=True, timeout=18)

    def test_real_429_recovers_with_exponential_backoff(self):
        self.failures = 2
        result = self.curl()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "ok")
        self.assertEqual(len(self.requests), 3)
        first = self.requests[1] - self.requests[0]
        second = self.requests[2] - self.requests[1]
        self.assertGreaterEqual(first, 0.8)
        self.assertGreaterEqual(second, first + 0.5)

    def test_real_persistent_429_fails_closed(self):
        self.failures = 100
        result = self.curl()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.requests), 3)

if __name__ == "__main__":
    unittest.main(verbosity=2)
