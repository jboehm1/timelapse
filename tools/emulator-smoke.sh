#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant eu.example.constructionlapse android.permission.CAMERA || true
adb shell pm grant eu.example.constructionlapse android.permission.POST_NOTIFICATIONS || true
adb shell am start -n eu.example.constructionlapse/.MainActivity
sleep 10
adb shell screencap -p /sdcard/app-home.png
adb pull /sdcard/app-home.png emulator-evidence/app-home.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
adb pull /sdcard/window.xml emulator-evidence/window.xml || true
adb logcat -d -v threadtime > emulator-evidence/logcat.txt || true
if grep -A 20 'FATAL EXCEPTION' emulator-evidence/logcat.txt | grep -q eu.example.constructionlapse; then
  echo 'App process crash on first boot' >&2
  exit 2
fi
echo 'Emulator installed, Activity launched, screenshot and logs saved.'
