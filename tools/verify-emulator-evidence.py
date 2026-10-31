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
import zipfile
from fractions import Fraction
from pathlib import Path
from PIL import Image


def validate_mp4(path: Path, frames: int, width: int, height: int, fps: int, duration_seconds: float) -> dict:
    if not shutil.which("ffprobe") or not shutil.which("ffmpeg"):
        raise RuntimeError("Independent validation requires both ffprobe and ffmpeg")
    probe = subprocess.run([
        "ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
        "-show_entries", "stream=codec_name,width,height,avg_frame_rate,nb_read_frames:format=duration",
        "-of", "json", str(path)
    ], check=True, capture_output=True, text=True, timeout=30)
    metadata = json.loads(probe.stdout)
    stream = metadata["streams"][0]
    duration = float(metadata["format"]["duration"])
    rate = float(Fraction(stream["avg_frame_rate"]))
    count = int(stream["nb_read_frames"])
    if (stream["codec_name"] != "h264" or (stream["width"], stream["height"]) != (width, height) or
            count != frames or abs(rate - fps) > 0.01 or abs(duration - duration_seconds) > 0.05):
        raise ValueError(f"ffprobe MP4 metadata mismatch for {path.name}: {stream}, duration={duration}")
    subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "null", "-"],
                   check=True, capture_output=True, timeout=60)
    return {"ffprobe": True, "ffmpeg_full_decode": True, "codec": stream["codec_name"],
            "width": stream["width"], "height": stream["height"], "fps": rate,
            "frames": count, "duration_seconds": duration}


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
        original_export = json.loads(read("original-export-validation.json"))
        expected_names = [row["file"] for row in rows]
        if (original_export.get("source_images") != len(rows) or
                original_export.get("sequence_images") != len(rows) or
                original_export.get("zip_images") != len(rows) or
                not original_export.get("all_source_hashes_preserved") or
                not original_export.get("all_output_hashes_match")):
            raise ValueError("Original sequence/ZIP export counts differ from captured JPEGs")
        sequence_hashes = {}
        (output / "original-sequence").mkdir(exist_ok=True)
        for row in rows:
            name = row["file"]
            content = read("original-sequence/" + name)
            if hashlib.sha256(content).hexdigest() != row["sha256"] or len(content) != int(row["bytes"]):
                raise ValueError("Individual original export differs from source hash")
            with Image.open(io.BytesIO(content)) as image:
                image.load()
                if image.format != "JPEG" or f"{image.width}x{image.height}" != row["dimensions"]:
                    raise ValueError("Individual original export failed host decode/dimension check")
            sequence_hashes[name] = hashlib.sha256(content).hexdigest()
            (output / "original-sequence" / name).write_bytes(content)
        zip_content = read("originals.zip")
        with zipfile.ZipFile(io.BytesIO(zip_content)) as zip_file:
            zip_names = zip_file.namelist()
            if zip_names != sorted(expected_names) or any("/" in name or "\\" in name for name in zip_names):
                raise ValueError("ZIP entries are missing, reordered, or contain parent paths")
            zipped_hashes = {name: hashlib.sha256(zip_file.read(name)).hexdigest() for name in zip_names}
        expected_hashes = {row["file"]: row["sha256"] for row in rows}
        if sequence_hashes != expected_hashes or zipped_hashes != expected_hashes:
            raise ValueError("Sequence or ZIP output hashes differ from source JPEGs")
        if (original_export.get("sequence_bytes") != sum(int(row["bytes"]) for row in rows) or
                original_export.get("zip_bytes") != sum(int(row["bytes"]) for row in rows)):
            raise ValueError("Original export byte counts differ from source bytes")
        (output / "originals.zip").write_bytes(zip_content)
        (output / "original-export-validation.json").write_text(json.dumps(original_export, indent=2) + "\n")
        videos = json.loads(read("video-validation.json"))
        if {row["effect"] for row in videos} != {"NONE", "MOTION_BLUR", "LIGHT_TRAILS"}:
            raise ValueError("Missing exported effect proof")
        (output / "exports").mkdir(exist_ok=True)
        host_video_outputs = {}
        for video in videos:
            name = video["file"]
            if not re.fullmatch(r"[a-z_]+\.mp4", name):
                raise ValueError("Invalid video evidence name")
            content = read("exports/" + name)
            if hashlib.sha256(content).hexdigest() != video["sha256"] or len(content) != video["bytes"] or len(content) < 100:
                raise ValueError("Video archive differs from tested MP4")
            if video["samples"] != 4 or video["width"] != 640 or video["height"] != 360:
                raise ValueError("Unexpected MP4 dimensions/frame accounting")
            target = output / "exports" / name
            target.write_bytes(content)
            host_video_outputs[video["effect"]] = validate_mp4(
                target, frames=video["samples"], width=video["width"], height=video["height"],
                fps=30, duration_seconds=video["duration_us"] / 1_000_000
            )
            video["host_validation"] = host_video_outputs[video["effect"]]
        (output / "video-validation.json").write_text(json.dumps(videos, indent=2) + "\n")
        performance = json.loads(read("export-performance.json"))
        if performance["uncached_decodes"] != 88 or performance["cached_decodes"] != 30 or performance["cache_hits"] != 58:
            raise ValueError("Sliding cache did not reduce repeated JPEG decoding")
        if performance["peak_cache_bytes"] > 4 * 640 * 360 * 4 or not performance["single_frame_pass"]:
            raise ValueError("Cache budget or one-frame export proof failed")
        batch = json.loads(read("batch-export-validation.json"))
        if batch != {"days": 2, "frames_per_day": 2, "kept_after_cancel": 1, "pending_after_cancel": 0, "state": "CANCELLED"}:
            raise ValueError("Day batch publication/cancellation proof failed")
        additional_videos = {}
        extra_specs = {
            "benchmark_uncached.mp4": performance["frames"],
            "benchmark_cached.mp4": performance["frames"],
            "single_frame.mp4": 1,
            "batch_day_1.mp4": 2,
            "batch_day_2.mp4": 2,
        }
        for name, expected_frames in extra_specs.items():
            content = read("exports/" + name)
            if len(content) < 100 or content[4:8] != b"ftyp":
                raise ValueError("Missing benchmark/batch MP4 bytes")
            target = output / "exports" / name
            target.write_bytes(content)
            additional_videos[name] = validate_mp4(
                target, frames=expected_frames, width=640, height=360, fps=30,
                duration_seconds=expected_frames / 30
            )
        walk = json.loads(read("person-walk-validation.json"))
        centroids = walk.get("person_centroids_x", [])
        if (walk.get("result") != "PASS" or walk.get("people") != 2 or walk.get("source_frames") != 7 or
                (walk.get("width"), walk.get("height"), walk.get("fps")) != (640, 360, 7) or
                walk.get("effect_window") != 5 or walk.get("pixels_delta_over_30", 0) <= 1000 or
                len(centroids) != 7 or any(after - before <= 30 for before, after in zip(centroids, centroids[1:]))):
            raise ValueError("Synthetic moving-person normal/blur comparison did not pass")
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
            host_validation = validate_mp4(target, frames=video["samples"], width=640, height=360,
                                           fps=7, duration_seconds=1.0)
            walk_outputs[mode] = {**video, "host_validation": host_validation}
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
        report = {"result": "PASS", "scope": "Android emulator only; S21 unverified", "jpeg_count": len(rows), "frames": images, "original_export": original_export, "videos": videos, "host_video_outputs": host_video_outputs, "additional_video_outputs": additional_videos, "person_walk_simulation": walk, "workspace": "PASS", "export_performance": performance, "day_batch": batch}
        (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
        return report


if __name__ == "__main__":
    report = verify(*sys.argv[1:])
    walk = report["person_walk_simulation"]
    print(f"PASS: {report['jpeg_count']} camera-simulator JPEGs decoded and hash-checked; synthetic person-walk Normal/Blur exports: {walk['pixels_delta_over_30']} middle-frame pixels differ")
