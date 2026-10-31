#!/usr/bin/env python3
"""Independently decode and hash real emulator JPEGs copied by instrumentation."""
import csv
import hashlib
import io
import json
import re
import shutil
import subprocess
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
        performance = json.loads(read("export-performance.json"))
        if performance["uncached_decodes"] != 88 or performance["cached_decodes"] != 30 or performance["cache_hits"] != 58:
            raise ValueError("Sliding cache did not reduce repeated JPEG decoding")
        if performance["peak_cache_bytes"] > 4 * 640 * 360 * 4 or not performance["single_frame_pass"]:
            raise ValueError("Cache budget or one-frame export proof failed")
        batch = json.loads(read("batch-export-validation.json"))
        if batch != {"days": 2, "frames_per_day": 2, "kept_after_cancel": 1, "pending_after_cancel": 0, "state": "CANCELLED"}:
            raise ValueError("Day batch publication/cancellation proof failed")
        for name in ("benchmark_uncached.mp4", "benchmark_cached.mp4", "single_frame.mp4", "batch_day_1.mp4", "batch_day_2.mp4"):
            content = read("exports/" + name)
            if len(content) < 100 or content[4:8] != b"ftyp":
                raise ValueError("Missing benchmark/batch MP4 bytes")
            (output / "exports" / name).write_bytes(content)
        walk = json.loads(read("person-walk-validation.json"))
        centroids = walk.get("person_centroids_x", [])
        if (walk.get("result") != "PASS" or walk.get("source_frames") != 7 or
                (walk.get("width"), walk.get("height"), walk.get("fps")) != (640, 360, 7) or
                walk.get("effect_window") != 5 or walk.get("pixels_delta_over_30", 0) <= 1000 or
                len(centroids) != 7 or any(after - before <= 30 for before, after in zip(centroids, centroids[1:]))):
            raise ValueError("Synthetic moving-person normal/blur comparison did not pass")
        if not shutil.which("ffprobe") or not shutil.which("ffmpeg"):
            raise RuntimeError("Independent validation requires both ffprobe and ffmpeg")
        walk_outputs = {}
        for mode in ("normal", "motion_blur"):
            video = walk[mode]
            name = video.get("file", "")
            if name not in {"person-walk-none.mp4", "person-walk-motion-blur.mp4"}:
                raise ValueError("Unexpected synthetic walk MP4 filename")
            content = read("exports/" + name)
            if (hashlib.sha256(content).hexdigest() != video.get("sha256") or
                    len(content) != video.get("bytes") or content[4:8] != b"ftyp"):
                raise ValueError("Synthetic walk video hash or MP4 header mismatch")
            if (video.get("samples"), video.get("width"), video.get("height")) != (7, 640, 360):
                raise ValueError("Unexpected synthetic walk MP4 dimensions/frame count")
            target = output / "exports" / name
            target.write_bytes(content)
            host_decode = {"ffprobe": False, "ffmpeg": False}
            if shutil.which("ffprobe"):
                probe = subprocess.run([
                    "ffprobe", "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height:format=duration", "-of", "json", str(target)
                ], check=True, capture_output=True, text=True, timeout=30)
                metadata = json.loads(probe.stdout)
                stream = metadata["streams"][0]
                duration = float(metadata["format"]["duration"])
                if (stream["width"], stream["height"]) != (640, 360) or abs(duration - 1.0) > 0.2:
                    raise ValueError("ffprobe reported unexpected synthetic walk video metadata")
                host_decode["ffprobe"] = True
                host_decode["duration_seconds"] = duration
            if shutil.which("ffmpeg"):
                subprocess.run([
                    "ffmpeg", "-v", "error", "-i", str(target), "-f", "null", "-"
                ], check=True, capture_output=True, timeout=60)
                host_decode["ffmpeg"] = True
            walk_outputs[mode] = {**video, "host_decode": host_decode}
        for name in ("person-walk-normal.png", "person-walk-blur.png", "person-walk-comparison.png"):
            content = read(name)
            with Image.open(io.BytesIO(content)) as image:
                image.load()
                if image.format != "PNG" or image.width <= 0 or image.height <= 0:
                    raise ValueError("Invalid synthetic walk comparison image")
                if name == "person-walk-comparison.png" and image.size != (1280, 404):
                    raise ValueError("Unexpected synthetic walk comparison dimensions")
            (output / name).write_bytes(content)
        walk["host_outputs"] = walk_outputs
        (output / "person-walk-validation.json").write_text(json.dumps(walk, indent=2) + "\n")
        (output / "export-performance.json").write_text(json.dumps(performance, indent=2) + "\n")
        (output / "batch-export-validation.json").write_text(json.dumps(batch, indent=2) + "\n")
        for name in ("projects-home.png", "planner.png", "project-gallery.png", "restored-project.png", "photolapse.png", "project-validation.txt"):
            member = archive.getmember("workspace-evidence/" + name)
            if not member.isfile() or member.size > 16 * 1024 * 1024:
                raise ValueError("Invalid workspace evidence")
            (output / name).write_bytes(archive.extractfile(member).read())
        report = {"result": "PASS", "scope": "Android emulator only; S21 unverified", "jpeg_count": len(rows), "frames": images, "videos": videos, "person_walk_simulation": walk, "workspace": "PASS", "export_performance": performance, "day_batch": batch}
        (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
        return report


if __name__ == "__main__":
    report = verify(*sys.argv[1:])
    walk = report["person_walk_simulation"]
    print(f"PASS: {report['jpeg_count']} camera-simulator JPEGs decoded and hash-checked; synthetic person-walk Normal/Blur exports: {walk['pixels_delta_over_30']} middle-frame pixels differ")
