#!/usr/bin/env python3
"""Fail if dependency-manifest merging adds permissions outside the source allowlist."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

from apk_permission_policy import verify_aapt_output


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: verify_apk_permissions.py path/to/app-debug.apk", file=sys.stderr)
        return 2
    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    def version(path: Path) -> tuple[int, ...]:
        return tuple(int(part) for part in path.parent.name.split(".") if part.isdigit())

    candidates = sorted((sdk / "build-tools").glob("*/aapt"), key=version, reverse=True)
    if not candidates:
        print("Android SDK aapt executable not found", file=sys.stderr)
        return 2
    result = subprocess.run([str(candidates[0]), "dump", "permissions", str(apk)],
                            check=True, capture_output=True, text=True)
    errors = verify_aapt_output(result.stdout)
    if errors:
        print("FAIL: merged APK permission policy")
        for error in errors:
            print(f"- {error}")
        return 1
    print("PASS: compiled APK permissions match the minimal local-only allowlist")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
