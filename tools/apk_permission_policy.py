"""Checks permissions merged into a built APK (including library manifests)."""
from __future__ import annotations

import re

from app_permission_policy import ALLOWED_PERMISSIONS

PERMISSION_LINE = re.compile(r"uses-permission(?:-[^:]+)?:\s+name=['\"]([^'\"]+)['\"]")


def parse_aapt_permissions(output: str) -> set[str]:
    return set(PERMISSION_LINE.findall(output))


def verify_aapt_output(output: str) -> list[str]:
    declared = parse_aapt_permissions(output)
    errors = []
    unexpected = declared - ALLOWED_PERMISSIONS
    missing = ALLOWED_PERMISSIONS - declared
    if unexpected:
        errors.append("Unexpected merged APK permissions: " + ", ".join(sorted(unexpected)))
    if missing:
        errors.append("Required merged APK permissions missing: " + ", ".join(sorted(missing)))
    return errors
