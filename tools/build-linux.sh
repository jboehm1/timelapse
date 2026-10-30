#!/usr/bin/env bash
# One-command Linux Android APK build: SDK CLI / platform / Gradle / tests.
# This bootstrap needs outbound HTTPS on first use; it does NOT require GitHub Actions.
# Android CLI SDK license acceptance is explicitly requested from the user.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="${TIMELAPSE_BUILD_CACHE:-$HOME/.cache/timelapse-android-build}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$TOOLS/android-sdk}}"
SDK_ARCHIVE="commandlinetools-linux-15859902_latest.zip"
SDK_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
GRADLE_VERSION="8.13"
GRADLE_ARCHIVE="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_BIN="$TOOLS/gradle-${GRADLE_VERSION}/bin/gradle"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

fail(){ echo "ERROR: $*" >&2; exit 1; }
log(){ printf '[timelapse/build] %s\n' "$*"; }
require(){ command -v "$1" >/dev/null || fail "Missing $1"; }
require curl
require unzip
require sha256sum
require java
mkdir -p "$TOOLS" "$SDK"

if [ ! -x "$GRADLE_BIN" ]; then
    log "Downloading Gradle $GRADLE_VERSION with official published checksum"
    curl -fL --retry 3 -o "$TOOLS/$GRADLE_ARCHIVE" "https://services.gradle.org/distributions/$GRADLE_ARCHIVE"
    curl -fL --retry 3 -o "$TOOLS/$GRADLE_ARCHIVE.sha256" "https://services.gradle.org/distributions/$GRADLE_ARCHIVE.sha256"
    expected="$(tr -d '[:space:]' < "$TOOLS/$GRADLE_ARCHIVE.sha256")"
    printf '%s  %s\n' "$expected" "$TOOLS/$GRADLE_ARCHIVE" | sha256sum -c - || fail "Gradle checksum mismatch"
    unzip -oq "$TOOLS/$GRADLE_ARCHIVE" -d "$TOOLS"
fi

if [ ! -x "$SDKMANAGER" ]; then
    log "Downloading official Android SDK command-line tools (181 MB)"
    curl -fL --retry 3 -o "$TOOLS/$SDK_ARCHIVE" "https://dl.google.com/android/repository/$SDK_ARCHIVE"
    printf '%s  %s\n' "$SDK_SHA256" "$TOOLS/$SDK_ARCHIVE" | sha256sum -c - || fail "Android SDK SHA256 mismatch"
    stage="$(mktemp -d)"
    trap 'rm -rf "$stage"' EXIT
    unzip -q "$TOOLS/$SDK_ARCHIVE" -d "$stage"
    [ -x "$stage/cmdline-tools/bin/sdkmanager" ] || fail "SDK tools archive unexpected structure"
    mkdir -p "$SDK/cmdline-tools"
    mv "$stage/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
if [ ! -f "$SDK/platforms/android-36/android.jar" ] || [ ! -x "$SDK/build-tools/35.0.0/aapt2" ]; then
    log "Install Android SDK Platform 36 and Build Tools. Accept the official SDK licenses interactively."
    "$SDKMANAGER" --sdk_root="$SDK" --licenses
    "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-36" "build-tools;35.0.0" "platform-tools"
fi

cd "$ROOT"
log "Android build started (repository: $ROOT)"
python3 tools/progress.py --check
"$GRADLE_BIN" --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -s "$APK" ] || fail "Gradle exited successfully but APK is absent"
mkdir -p "$ROOT/dist"
cp "$APK" "$ROOT/dist/Timelapse-debug.apk"
sha256sum "$ROOT/dist/Timelapse-debug.apk"
log "SUCCESS: $ROOT/dist/Timelapse-debug.apk (debug signed, NOT device validated)"
