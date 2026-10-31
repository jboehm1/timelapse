#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
# Always preserve evidence, including failing startup/capture traces.
collect() {
  adb shell dumpsys window windows > emulator-evidence/windows.txt || true
  adb shell dumpsys activity activities > emulator-evidence/activities.txt || true
  adb logcat -d -v threadtime > emulator-evidence/logcat.txt || true
  adb exec-out run-as eu.example.constructionlapse tar -C files -cf - recording-evidence workspace-evidence \
    > emulator-evidence/recording-evidence.tar 2>/dev/null || true
}
trap collect EXIT
adb wait-for-device
# UI text is entered through Compose semantics; keep the virtual IME from occluding action targets.
adb shell settings put secure show_ime_with_hard_keyboard 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant eu.example.constructionlapse android.permission.CAMERA || true
adb shell pm grant eu.example.constructionlapse android.permission.POST_NOTIFICATIONS || true
adb shell am start -n eu.example.constructionlapse/.MainActivity
sleep 10
adb shell screencap -p /sdcard/app-home.png
adb pull /sdcard/app-home.png emulator-evidence/app-home.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
adb pull /sdcard/window.xml emulator-evidence/window.xml || true
# Exercise the actual on-device Settings dialog and collect its layout.
if [ -s emulator-evidence/window.xml ]; then
  coords="$(python3 - <<'PY'
import re, xml.etree.ElementTree as E
root=E.parse('emulator-evidence/window.xml').getroot()
for n in root.iter('node'):
    if 'SETTINGS' in n.attrib.get('text','').upper():
        bounds=n.attrib.get('bounds','')
        values=list(map(int,re.findall(r'\d+',bounds)))
        if len(values)==4:
            print((values[0]+values[2])//2,(values[1]+values[3])//2)
            break
PY
)"
  if [ -n "$coords" ]; then
    adb shell input tap $coords
    sleep 3
    adb shell screencap -p /sdcard/app-settings.png
    adb pull /sdcard/app-settings.png emulator-evidence/app-settings.png
    adb shell uiautomator dump /sdcard/settings.xml >/dev/null || true
    adb pull /sdcard/settings.xml emulator-evidence/settings.xml || true
    adb shell input swipe 530 1780 530 590 400
    sleep 1
    adb shell screencap -p /sdcard/app-settings-scroll.png
    adb pull /sdcard/app-settings-scroll.png emulator-evidence/app-settings-scroll.png
  fi
fi
adb logcat -d -v threadtime > emulator-evidence/logcat.txt || true
if grep -A 20 'FATAL EXCEPTION' emulator-evidence/logcat.txt | grep -q eu.example.constructionlapse; then
  echo 'App process crash on first boot' >&2
  exit 2
fi
# Run the real UI recording gate. The instrumentation independently decodes all
# JPEGs, verifies hashes/index, backgrounds the Activity and reopens the project.
adb shell input keyevent 4
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 420s adb shell am instrument -w -r \
  -e class eu.example.constructionlapse.RecordingSmokeTest,eu.example.constructionlapse.WorkspaceSmokeTest,eu.example.constructionlapse.PersonWalkExportSimulationTest \
  eu.example.constructionlapse.test/androidx.test.runner.AndroidJUnitRunner \
  | tee emulator-evidence/instrumentation.txt
grep -q 'OK (4 tests)' emulator-evidence/instrumentation.txt
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' emulator-evidence/instrumentation.txt; then
  exit 3
fi
# run-as cannot access /sdcard on every Android image. The test explicitly
# copies its synthetic JPEGs to private test evidence after validating originals.
collect
python3 tools/verify-emulator-evidence.py emulator-evidence/recording-evidence.tar emulator-evidence/verified
echo 'PASS: camera JPEGs independently decoded on host; background capture and persistence verified on emulator.'
