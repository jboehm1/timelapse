#!/usr/bin/env python3
"""Regression tests for the source manifest and local-only policy."""
import tempfile
import unittest
from pathlib import Path

from app_permission_policy import verify_manifest, verify_source


class PermissionPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="timelapse-policy-")
        self.root = Path(self.temp.name)
        self.manifest = self.root / "AndroidManifest.xml"
        self.allowed = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <uses-permission android:name="android.permission.CAMERA" />
          <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
          <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
          <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
          <uses-permission android:name="android.permission.WAKE_LOCK" />
          <application android:allowBackup="false" android:usesCleartextTraffic="false">
            <activity android:name=".MainActivity" android:exported="true"><intent-filter /></activity>
            <service android:name=".CaptureService" android:exported="false" />
            <receiver android:name=".ScheduleReceiver" android:exported="false" />
          </application>
        </manifest>"""

    def tearDown(self):
        self.temp.cleanup()

    def test_minimal_camera_manifest_passes(self):
        self.manifest.write_text(self.allowed)
        self.assertEqual([], verify_manifest(self.manifest))

    def test_internet_permission_is_rejected(self):
        self.manifest.write_text(self.allowed.replace(
            "<application", '<uses-permission android:name="android.permission.INTERNET" /><application'))
        self.assertTrue(any("Unexpected permissions" in x for x in verify_manifest(self.manifest)))

    def test_sdk_scoped_internet_permission_is_rejected(self):
        self.manifest.write_text(self.allowed.replace(
            "<application", '<uses-permission-sdk-23 android:name="android.permission.INTERNET" /><application'))
        self.assertTrue(any("Unexpected permissions" in x for x in verify_manifest(self.manifest)))

    def test_backup_and_cleartext_must_be_explicitly_disabled(self):
        self.manifest.write_text(self.allowed.replace('android:allowBackup="false"', '')
                                  .replace('android:usesCleartextTraffic="false"', ''))
        errors = verify_manifest(self.manifest)
        self.assertTrue(any("backup must remain disabled" in x for x in errors))
        self.assertTrue(any("Cleartext traffic must remain disabled" in x for x in errors))

    def test_exported_internal_service_is_rejected(self):
        self.manifest.write_text(self.allowed.replace(
            'android:name=".CaptureService" android:exported="false"',
            'android:name=".CaptureService" android:exported="true"'))
        self.assertTrue(any("must explicitly set exported=false" in x for x in verify_manifest(self.manifest)))

    def test_service_export_state_must_be_explicit(self):
        self.manifest.write_text(self.allowed.replace(
            'android:name=".CaptureService" android:exported="false"',
            'android:name=".CaptureService"'))
        self.assertTrue(any("must explicitly set exported" in x for x in verify_manifest(self.manifest)))

    def test_network_client_in_source_is_rejected(self):
        path = self.root / "app/src/main/java/Remote.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import okhttp3.OkHttpClient\n")
        self.assertTrue(any("Network API" in x for x in verify_source(self.root)))

    def test_wildcard_java_net_import_is_rejected(self):
        path = self.root / "app/src/main/java/Remote.java"
        path.parent.mkdir(parents=True)
        path.write_text("import java.net.*;\n")
        self.assertTrue(any("Network API" in x for x in verify_source(self.root)))

    def test_local_file_source_passes(self):
        path = self.root / "app/src/main/java/Store.kt"
        path.parent.mkdir(parents=True)
        path.write_text("import java.io.File\nfun originals() = File(\"frames\")")
        self.assertEqual([], verify_source(self.root))


if __name__ == "__main__":
    unittest.main(verbosity=2)
