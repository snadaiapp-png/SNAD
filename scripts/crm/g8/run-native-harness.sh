#!/usr/bin/env bash
# =============================================================================
# G8 Track E — Android-native harness (module compile + JVM unit tests + AAR)
# -----------------------------------------------------------------------------
# Validates the sanad-call-screening module against the REAL expo-modules-core
# android sources WITHOUT the expo CLI: the canonical `expo prebuild` chain is
# blocked in this dependency set by an SDK-52 packaging quirk (packages ship
# Metro-style TS/extensionless entry points that modern Node cannot require),
# which is unrelated to the module and reproduces on an empty plugin list.
#
# The harness mirrors exactly what a full app build would do for the module:
#   - expo-modules-core compiled from the workspace node_modules
#   - kotlin targets 17, compileSdk 35, minSdk 24 (app defaults)
#   - react-android 0.79.4 resolved from Maven Central (the version the full
#     Expo app injects; the module declares the dependency versionless)
#   - :sanad-call-screening:testDebugUnitTest + :sanad-call-screening:assembleDebug
#
# Usage (from repo root; gradle 8.9+ on PATH; ANDROID_HOME set):
#   bash scripts/crm/g8/run-native-harness.sh [gradle-extra-args...]
# Exit 0 = PASS.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MOBILE="$ROOT/apps/mobile"
MODULE="$MOBILE/modules/sanad-call-screening"
HARNESS="$(mktemp -d)"
trap 'rm -rf "$HARNESS"' EXIT

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"

echo "[harness] writing project files into $HARNESS"

cat > "$HARNESS/settings.gradle" <<EOF
pluginManagement {
  repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.PREFER_PROJECT
  repositories { google(); mavenCentral() }
}
rootProject.name = 'g8-native-harness'
include ':expo-modules-core'
project(':expo-modules-core').projectDir = file('$MOBILE/node_modules/expo-modules-core/android')
include ':sanad-call-screening'
project(':sanad-call-screening').projectDir = file('$MODULE/android')
EOF

cat > "$HARNESS/build.gradle" <<'EOF'
buildscript {
  ext.kotlinVersion = '1.9.24'
  repositories { google(); mavenCentral(); gradlePluginPortal() }
  dependencies {
    classpath 'com.android.tools.build:gradle:8.7.3'
    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
  }
}
rootProject.ext.compileSdkVersion = 35
rootProject.ext.minSdkVersion = 24
rootProject.ext.targetSdkVersion = 34

// Expo apps set Java/Kotlin targets to 17 at the root; mirror that here.
subprojects {
  afterEvaluate { p ->
    if (p.plugins.hasPlugin('com.android.library') || p.plugins.hasPlugin('com.android.application')) {
      p.android {
        compileOptions {
          sourceCompatibility JavaVersion.VERSION_17
          targetCompatibility JavaVersion.VERSION_17
        }
      }
    }
    if (p.plugins.hasPlugin('org.jetbrains.kotlin.android')) {
      p.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        kotlinOptions { jvmTarget = '17' }
      }
    }
  }
}

// expo-modules-core declares `implementation 'com.facebook.react:react-android'`
// versionless; the full Expo app injects the version — supply it here.
allprojects {
  configurations.configureEach {
    resolutionStrategy.eachDependency { details ->
      if (details.requested.group == 'com.facebook.react' && details.requested.name == 'react-android' && details.requested.version?.trim()?.isEmpty()) {
        details.useVersion '0.79.4'
      }
    }
  }
}
EOF

cat > "$HARNESS/gradle.properties" <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=false
EOF

# Node must resolve react-native from the harness cwd (expo-modules-core does
# `node --print require.resolve('react-native/package.json')` with workDir=rootDir).
ln -s "$MOBILE/node_modules" "$HARNESS/node_modules"

echo "sdk.dir=$ANDROID_HOME" > "$HARNESS/local.properties"

echo "[harness] running gradle (module compile + JVM tests + AAR)..."
gradle -p "$HARNESS" \
  :sanad-call-screening:testDebugUnitTest \
  :sanad-call-screening:assembleDebug \
  --console=plain "$@"
echo "[harness] NATIVE_HARNESS: PASS"
