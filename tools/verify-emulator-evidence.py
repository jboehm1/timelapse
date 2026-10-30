#!/usr/bin/env python3
"""Independently decode and hash real emulator JPEGs copied by instrumentation."""
import csv
import hashlib
import io
import json
import re
import sys
import tarfile
from pathlib import Path
from PIL import Image


def verify(archive_path: str, destination: str) -> dict:
    output = Path(destination)
    output.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_path) as archive:
        def read(name):
            member = archive.getmember("recording-evidence/" + name)
            if not member.isfile() or member.size > 32 * 1024 * 1024:
                raise ValueError("Unexpected or oversized evidence member")
            return archive.extractfile(member).read()

        table = read("jpeg-validation.tsv")
        rows = list(csv.DictReader(io.StringIO(table.decode()), delimiter="\t"))
        if len(rows) < 12:
            raise ValueError("Fewer than twelve validated originals")
        ids = []
        images = []
        for row in rows:
            name = row["file"]
            match = re.fullmatch(r"frame_([0-9]{10})_([0-9]+)\.jpg", name)
            if not match:
                raise ValueError("Invalid original filename")
            ids.append(int(match[1]))
            content = read("originals/" + name)
            if len(content) != int(row["bytes"]) or hashlib.sha256(content).hexdigest() != row["sha256"]:
                raise ValueError("Original hash/length differs from device evidence")
            if not content.startswith(b"\xff\xd8") or not content.endswith(b"\xff\xd9"):
                raise ValueError("JPEG marker check failed")
            with Image.open(io.BytesIO(content)) as image:
                image.load()  # full independent decode, not just header inspection
                if image.format != "JPEG" or image.width <= 0 or image.height <= 0:
                    raise ValueError("Invalid JPEG dimensions/format")
                if f"{image.width}x{image.height}" != row["dimensions"]:
                    raise ValueError("Dimensions differ from device decoder")
                images.append(dict(row, width=image.width, height=image.height))
            (output / "originals").mkdir(exist_ok=True)
            (output / "originals" / name).write_bytes(content)
        if ids != sorted(set(ids)):
            raise ValueError("Non-monotonic or duplicate frame IDs")
        for name in ("idle.png", "starting.png", "recording.png", "reopened.png", "invalid-camera.png", "invalid-camera.txt"):
            (output / name).write_bytes(read(name))
        (output / "jpeg-validation.tsv").write_bytes(table)
        videos = json.loads(read("video-validation.json"))
        if {row["effect"] for row in videos} != {"NONE", "MOTION_BLUR", "LIGHT_TRAILS"}:
            raise ValueError("Missing exported effect proof")
        (output / "exports").mkdir(exist_ok=True)
        for video in videos:
            name = video["file"]
            if not re.fullmatch(r"[a-z_]+\.mp4", name):
                raise ValueError("Invalid video evidence name")
            content = read("exports/" + name)
            if hashlib.sha256(content).hexdigest() != video["sha256"] or len(content) != video["bytes"] or len(content) < 100:
                raise ValueError("Video archive differs from tested MP4")
            if video["samples"] != 4 or video["width"] != 640 or video["height"] != 360:
                raise ValueError("Unexpected MP4 dimensions/frame accounting")
            (output / "exports" / name).write_bytes(content)
        (output / "video-validation.json").write_text(json.dumps(videos, indent=2) + "\n")
        for name in ("projects-home.png", "planner.png", "project-gallery.png", "restored-project.png", "project-validation.txt"):
            member = archive.getmember("workspace-evidence/" + name)
            if not member.isfile() or member.size > 16 * 1024 * 1024:
                raise ValueError("Invalid workspace evidence")
            (output / name).write_bytes(archive.extractfile(member).read())
        report = {"result": "PASS", "scope": "Android emulator only; S21 unverified", "jpeg_count": len(rows), "frames": images, "videos": videos, "workspace": "PASS"}
        (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
        return report


if __name__ == "__main__":
    report = verify(*sys.argv[1:])
    print(f"PASS: {report['jpeg_count']} archived camera JPEGs decoded and hashes matched independently on host")
