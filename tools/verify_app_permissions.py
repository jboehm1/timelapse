#!/usr/bin/env python3
"""Check that capture stays local and Android permissions remain minimal."""
from pathlib import Path

from app_permission_policy import verify

ROOT = Path(__file__).resolve().parent.parent
errors = verify(ROOT)
if errors:
    print("FAIL: application permission/privacy policy")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("PASS: minimal camera permissions, internal components, and no app network API")
