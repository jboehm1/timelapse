"""Static privacy/permission contract for the local-first Android app."""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ALLOWED_PERMISSIONS = {
    "android.permission.CAMERA",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CAMERA",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
}
NETWORK_API = re.compile(
    r"(?:java\.net\.(?:HttpURLConnection|URL|Socket|ServerSocket)|java\.net\.\*|"
    r"okhttp3\.|retrofit2\.|android\.app\.DownloadManager|"
    r"android\.webkit\.WebView)", re.IGNORECASE
)


def verify_manifest(path: Path) -> list[str]:
    root = ET.parse(path).getroot()
    errors: list[str] = []
    # Also inspect SDK-scoped variants so they cannot bypass the allowlist.
    permission_nodes = [node for node in root if node.tag.startswith("uses-permission")]
    declared = {node.get(ANDROID + "name", "") for node in permission_nodes}
    unexpected = declared - ALLOWED_PERMISSIONS
    missing = ALLOWED_PERMISSIONS - declared
    if unexpected:
        errors.append("Unexpected permissions: " + ", ".join(sorted(unexpected)))
    if missing:
        errors.append("Required permissions missing: " + ", ".join(sorted(missing)))

    app = root.find("application")
    if app is None:
        return errors + ["Application element missing"]
    if app.get(ANDROID + "allowBackup", "true").lower() != "false":
        errors.append("Application backup must remain disabled until backup privacy is reviewed")
    if app.get(ANDROID + "usesCleartextTraffic", "true").lower() != "false":
        errors.append("Cleartext traffic must remain disabled")
    for kind in ("activity", "service", "receiver", "provider"):
        for component in app.findall(kind):
            name = component.get(ANDROID + "name", "<unnamed>")
            if component.get(ANDROID + "exported") is None:
                errors.append(f"{kind.title()} {name} must explicitly set exported")
            if component.get(ANDROID + "exported", "false").lower() == "true" and not component.findall("intent-filter"):
                errors.append(f"Exported {kind} {name} has no documented intent filter")
            if kind in {"service", "receiver", "provider"} and component.get(ANDROID + "exported", "false").lower() != "false":
                errors.append(f"Internal {kind} {name} must explicitly set exported=false")
    return errors


def verify_source(root: Path) -> list[str]:
    errors: list[str] = []
    source_root = root / "app/src/main"
    source_files = sorted(set(source_root.rglob("*.kt")) | set(source_root.rglob("*.java")))
    for path in source_files:
        source = path.read_text(encoding="utf-8")
        match = NETWORK_API.search(source)
        if match:
            errors.append(f"Network API {match.group(0)} found in {path.relative_to(root)}")
    return errors


def verify(root: Path) -> list[str]:
    return verify_manifest(root / "app/src/main/AndroidManifest.xml") + verify_source(root)
