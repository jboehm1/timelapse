#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
# Compile all independent policy suites together; one compiler startup suffices.
kotlinc \
  core/CapturePolicy.kt tools/jvm-tests/CapturePolicyTest.kt \
  core/FrameStore.kt tools/jvm-tests/FrameStoreTest.kt \
  core/TimelapseLogic.kt tools/jvm-tests/TimelapseLogicTest.kt \
  -include-runtime -d "$out/policy.jar"
java -cp "$out/policy.jar" eu.example.constructionlapse.CapturePolicyTestKt
java -cp "$out/policy.jar" eu.example.constructionlapse.FrameStoreTestKt
java -cp "$out/policy.jar" TimelapseLogicTestKt
python3 tools/progress.py --check
