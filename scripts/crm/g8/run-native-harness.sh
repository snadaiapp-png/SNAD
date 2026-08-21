#!/usr/bin/env bash
# =============================================================================
# G8 Track E — Android-native harness (module compile + JVM unit tests + AAR)
# -----------------------------------------------------------------------------
# SDK 53 approach: run module tasks inside the REAL generated Expo Android
# project graph. The old standalone temp-Gradle approach broke because
# expo-modules-core/android/build.gradle (SDK 53) imports
# expo.modules.plugin.gradle.ExpoModuleExtension which requires the Expo
# Gradle plugin classpath — only available inside the full prebuild graph.
#
# This harness:
#   1. Runs `npx expo prebuild --platform android --clean --no-install`
#   2. From the generated android/ directory, runs:
#      ./gradlew :sanad-call-screening:testDebugUnitTest
#      ./gradlew :sanad-call-screening:assembleDebug
#   3. Captures reports
#
# Usage (from apps/mobile; ANDROID_HOME set; Node 20 on PATH):
#   bash ../../scripts/crm/g8/run-native-harness.sh
# Exit 0 = PASS.
# =============================================================================
set -euo pipefail

echo "[harness] SDK53 approach: prebuild + module tasks in real Expo graph"

# Step 1: Clean prebuild to generate the full Android project
echo "[harness] Running expo prebuild (clean)..."
npx expo prebuild --platform android --clean --no-install
echo "[harness] Prebuild complete"

# Step 2: Run module-level tasks inside the real Gradle graph
cd android

echo "[harness] Discovering module tasks..."
MODULE_TASKS=$(./gradlew tasks --all --no-daemon 2>/dev/null | grep -i "sanad-call-screening" | head -20 || true)
echo "[harness] Available module tasks:"
echo "$MODULE_TASKS"

# Step 3: Run unit tests for the sanad-call-screening module
echo "[harness] Running :sanad-call-screening:testDebugUnitTest..."
./gradlew :sanad-call-screening:testDebugUnitTest --no-daemon -x lint
echo "[harness] NATIVE_KOTLIN_TESTS: PASS"

# Step 4: Build the AAR for the sanad-call-screening module
echo "[harness] Running :sanad-call-screening:assembleDebug..."
./gradlew :sanad-call-screening:assembleDebug --no-daemon -x lint
echo "[harness] NATIVE_MODULE_COMPILE: PASS"
echo "[harness] NATIVE_AAR: PASS"

echo "[harness] NATIVE_HARNESS: PASS"
