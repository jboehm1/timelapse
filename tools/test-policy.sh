#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc core/CapturePolicy.kt tools/jvm-tests/CapturePolicyTest.kt -include-runtime -d "$out/policy.jar"
java -jar "$out/policy.jar"
