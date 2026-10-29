#!/usr/bin/env python3
"""Validate and regenerate docs/MASTER_TODO.md from the canonical MASTER_TODO.csv.

Usage (Python 3, no third-party dependencies):
    python tools/progress.py               # print current weighted progress
    python tools/progress.py --write       # update markdown dashboard and checklist
    python tools/progress.py --check       # CI: fail if markdown and CSV differ

Development policy: A score increase requires a test/commit/evidence documented in PR.
See docs/MASTER_TODO.md. This tool enforces formatting, not factual test claims.
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_FILE = ROOT / "docs" / "MASTER_TODO.csv"
MD_FILE = ROOT / "docs" / "MASTER_TODO.md"
ALLOWED = {0, 10, 25, 40, 55, 75, 90, 100}
WEIGHTS = {"P0": 5, "P1": 3, "P2": 1}
GROUP_TITLES = {
    "A": "Build, outils, CI et livraison",
    "B": "Projets, Photolapse, import et stockage",
    "C": "Capture caméra, objectifs, format et qualité",
    "D": "Focus, exposition, balances et interface de mesure",
    "E": "Programmation, écran OFF et consommation",
    "F": "Fiabilité, sécurité matérielle et récupération",
    "G": "Export, formats, effets et montage vidéo",
    "H": "Commandes externes, UI, ergonomie",
    "I": "Confidentialité, sécurité, droits, commercialisation",
    "J": "Tests, mesures, robustesse validée et release",
    "K": "Documentation, gouvernance et suivi du projet",
}


def load_tasks() -> list[dict]:
    with CSV_FILE.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        expected = ["id", "domain", "priority", "progress_percent", "name", "acceptance"]
        if reader.fieldnames != expected:
            raise ValueError(f"CSV columns must be {expected!r}")
        tasks = list(reader)
    if not tasks:
        raise ValueError("Empty task list")
    ids = set()
    for task in tasks:
        if task["id"] in ids:
            raise ValueError(f"Duplicate ID: {task['id']}")
        ids.add(task["id"])
        domain = task["domain"]
        if domain not in GROUP_TITLES or not re.fullmatch(rf"{domain}-[0-9]{{2}}", task["id"]):
            raise ValueError(f"Invalid ID/domain: {task['id']}/{domain}")
        if task["priority"] not in WEIGHTS:
            raise ValueError(f"Unknown priority: {task['id']}")
        pct = int(task["progress_percent"])
        if pct not in ALLOWED:
            raise ValueError(f"Invalid score {task['id']}={pct}, must be one of {sorted(ALLOWED)}")
        task["percent"] = pct
        if not task["name"].strip() or not task["acceptance"].strip():
            raise ValueError(f"Missing title or acceptance criterion: {task['id']}")
        if any("|" in task[k] for k in ("name", "acceptance")):
            raise ValueError(f"Unescaped markdown pipe in {task['id']}")
    return tasks


def progress(tasks: list[dict]) -> int:
    denominator = sum(WEIGHTS[task["priority"]] for task in tasks)
    return round(sum(task["percent"] * WEIGHTS[task["priority"]] for task in tasks) / denominator)


def render_score(tasks: list[dict]) -> str:
    full = sum(task["percent"] == 100 for task in tasks)
    lines = [
        f"**Progression pondérée estimée : {progress(tasks)} %** — **{full}/{len(tasks)} tâches à 100 %** "
        "(documents / politique uniquement). Aucun **gate** prototype Android n'est encore validé. "
        "**Les workflows GitHub Actions de la PR #2 échouent avant l'allocation de runner ; "
        "aucun APK n'a été produit.** Ce constat n'est pas un échec démontré du compilateur Kotlin.",
        "",
        "| Domaine | Tâches | Score pondéré |",
        "|---|---:|---:|",
    ]
    for domain, title in GROUP_TITLES.items():
        group = [task for task in tasks if task["domain"] == domain]
        lines.append(f"| {domain} — {title} | {len(group)} | **{progress(group)} %** |")
    return "\n".join(lines)


def render_checklist(tasks: list[dict]) -> str:
    sections = []
    for domain, title in GROUP_TITLES.items():
        group = [task for task in tasks if task["domain"] == domain]
        sections.extend([
            f"### {domain}. {title} — {progress(group)} %",
            "",
            "| Fait | ID | Priorité | Avancement | Fonction / garde-fou | Critère de réussite |",
            "|---|---|---|---:|---|---|",
        ])
        for task in group:
            check = "[x]" if task["percent"] == 100 else "[ ]"
            sections.append(
                f"| {check} | {task['id']} | {task['priority']} | **{task['percent']} %** "
                f"| {task['name']} | {task['acceptance']} |"
            )
        sections.append("")
    return "\n".join(sections).rstrip()


def replace_marked(text: str, name: str, body: str) -> str:
    start = f"<!-- PROGRESS-{name}-START -->"
    end = f"<!-- PROGRESS-{name}-END -->"
    if text.count(start) != 1 or text.count(end) != 1:
        raise ValueError(f"Expected one pair of markers for {name}")
    left, rest = text.split(start, 1)
    _, right = rest.split(end, 1)
    return left + start + "\n\n" + body.rstrip() + "\n\n" + end + right


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true")
    group.add_argument("--write", action="store_true")
    args = parser.parse_args()

    tasks = load_tasks()
    existing = MD_FILE.read_text(encoding="utf-8")
    expected = replace_marked(existing, "SCORE", render_score(tasks))
    expected = replace_marked(expected, "LIST", render_checklist(tasks))
    total = progress(tasks)
    done = sum(task["percent"] == 100 for task in tasks)
    print(f"MASTER_TODO: {len(tasks)} items; {done} at 100%; overall weighted: {total}%")
    if args.write:
        MD_FILE.write_text(expected, encoding="utf-8")
        print("Updated docs/MASTER_TODO.md")
    elif args.check and existing != expected:
        print("MASTER_TODO.md is out of sync. Run: python tools/progress.py --write", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
