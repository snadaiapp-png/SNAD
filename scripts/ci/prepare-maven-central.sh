#!/usr/bin/env bash
# Set up a job-scoped Maven repository with a consistent Central mirror ID.
# Only use known HTTPS Central endpoints; never rewrite resolver provenance.
set -euo pipefail
: "${GITHUB_ENV:?GitHub Actions environment file required}"
: "${RUNNER_TEMP:?GitHub Actions temp directory required}"
: "${HOME:?HOME required}"
# Maven 3.9+ reads MAVEN_ARGS before command-line arguments.
version="$(mvn --version)"
grep -Eq 'Apache Maven 3\.([9]|[1-9][0-9]+)\.' <<< "$version" || {
  echo "::error::Maven 3.9+ required for Maven CI transport configuration" >&2; exit 1;
}
settings="$RUNNER_TEMP/snad-ci-central-settings.xml"
repo="$HOME/.m2/snad-ci-central-v1"
artifact="org/springframework/boot/spring-boot-starter-parent/3.5.6/spring-boot-starter-parent-3.5.6.pom"
probe="$(mktemp "$RUNNER_TEMP/snad-central-probe.XXXXXX")"
trap 'rm -f "$probe"' EXIT
selected=""
for endpoint in https://repo.maven.apache.org/maven2 https://repo1.maven.org/maven2 https://maven-central.storage-download.googleapis.com/maven2; do
  # curl retries HTTP 429/5xx with exponential backoff (no fixed --retry-delay).
  # Other HTTP failures and malformed POMs continue to the next approved mirror.
  if curl --fail --silent --show-error --connect-timeout 8 --max-time 20 \
     --retry 3 --retry-max-time 90 \
     -o "$probe" "$endpoint/$artifact" &&
     python3 - "$probe" <<'PY'
import sys
import xml.etree.ElementTree as ET
try:
    r = ET.parse(sys.argv[1]).getroot()
    ns = {"p": "http://maven.apache.org/POM/4.0.0"}
    assert r.tag == "{http://maven.apache.org/POM/4.0.0}project"
    assert r.findtext("p:artifactId", namespaces=ns) == "spring-boot-starter-parent"
    assert r.findtext("p:parent/p:groupId", namespaces=ns) == "org.springframework.boot"
    assert r.findtext("p:parent/p:artifactId", namespaces=ns) == "spring-boot-dependencies"
    assert r.findtext("p:parent/p:version", namespaces=ns) == "3.5.6"
    assert r.findtext("p:version", namespaces=ns) in (None, "3.5.6")
except (AssertionError, OSError, ET.ParseError):
    sys.exit(1)
PY
  then
    selected="$endpoint"
    break
  fi
  echo "::warning::Maven Central probe failed: $endpoint"
done
[[ -n "$selected" ]] || { echo "::error::MAVEN_CENTRAL_UNAVAILABLE: all approved endpoint probes failed"; exit 1; }
mkdir -p "$repo"
cat > "$settings" <<XML
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors>
    <mirror>
      <id>snad-ci-central-v1</id>
      <name>SNAD CI Central</name>
      <url>$selected</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
XML
# Ubuntu runner paths do not contain whitespace. Fail rather than misparse arguments.
for path in "$settings" "$repo"; do
  [[ ! "$path" =~ [[:space:]] ]] || { echo "::error::unsupported whitespace in Maven CI path"; exit 1; }
done
printf 'MAVEN_ARGS=--settings %s -Dmaven.repo.local=%s --strict-checksums\n' "$settings" "$repo" >> "$GITHUB_ENV"
printf 'MAVEN_CI_ENDPOINT=%s\n' "$selected" >> "$GITHUB_ENV"
echo "MAVEN_CI_ENDPOINT=$selected; repository ID=snad-ci-central-v1"
