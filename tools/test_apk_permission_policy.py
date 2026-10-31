#!/usr/bin/env python3
"""Unit tests for compiled APK permission extraction and allowlist enforcement."""
import unittest

from apk_permission_policy import verify_aapt_output


ALLOWED = """package: eu.example.constructionlapse
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
uses-permission: name='android.permission.FOREGROUND_SERVICE_CAMERA'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.WAKE_LOCK'
"""


class CompiledApkPermissionPolicyTest(unittest.TestCase):
    def test_minimal_compiled_apk_permissions_pass(self):
        self.assertEqual([], verify_aapt_output(ALLOWED))

    def test_expected_androidx_camera_permissions_pass(self):
        merged = ALLOWED + (
            "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'\n"
            "uses-permission: name='eu.example.constructionlapse.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'\n"
        )
        self.assertEqual([], verify_aapt_output(merged))

    def test_dependency_added_internet_permission_fails(self):
        result = verify_aapt_output(ALLOWED + "uses-permission: name='android.permission.INTERNET'\n")
        self.assertTrue(any("INTERNET" in error for error in result))

    def test_missing_camera_permission_fails(self):
        result = verify_aapt_output("\n".join(line for line in ALLOWED.splitlines() if "CAMERA" not in line))
        self.assertTrue(any("CAMERA" in error for error in result))

    def test_sdk_scoped_network_permission_fails(self):
        result = verify_aapt_output(ALLOWED + "uses-permission-sdk-23: name='android.permission.INTERNET'\n")
        self.assertTrue(any("INTERNET" in error for error in result))


if __name__ == "__main__":
    unittest.main(verbosity=2)
