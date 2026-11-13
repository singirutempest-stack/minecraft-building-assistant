"""Generates the built-in schematics and their translations.

Usage: python3 tools/schematics/generate.py

Writes one JSON file per build into the mod's resources and updates the schematic name and description
entries of the language files. Run it after changing any build; the generated files are committed, so
building the mod does not need Python.
"""
import json
import sys
from pathlib import Path

import builds_survival

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "buildingassistant"
SCHEMATICS = ASSETS / "schematics"
LANG = ASSETS / "lang"

MODULES = [builds_survival]
SCHEMATIC_KEY_PREFIX = "buildingassistant.schematic."


def update_lang(path, entries):
    """Replaces all schematic entries of a language file, keeping every other key as it is."""
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    kept = {key: value for key, value in current.items() if not key.startswith(SCHEMATIC_KEY_PREFIX)}
    kept.update(entries)
    path.write_text(json.dumps(kept, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    builds = [factory() for module in MODULES for factory in module.BUILDS]
    ids = [build.id for build in builds]
    duplicates = {build_id for build_id in ids if ids.count(build_id) > 1}
    if duplicates:
        sys.exit(f"duplicate schematic ids: {sorted(duplicates)}")

    SCHEMATICS.mkdir(parents=True, exist_ok=True)
    written = set()
    for build in builds:
        target = SCHEMATICS / f"{build.id}.json"
        target.write_text(build.to_json(), encoding="utf-8")
        written.add(target.name)
        print(f"{build.id}: {build.size[0]}x{build.size[1]}x{build.size[2]}, {len(build.cells)} blocks")

    stale = sorted(path.name for path in SCHEMATICS.glob("*.json") if path.name not in written)
    if stale:
        sys.exit(f"schematic files without a build definition, remove them by hand: {stale}")

    english, russian = {}, {}
    for build in builds:
        english[f"{SCHEMATIC_KEY_PREFIX}{build.id}.name"] = build.name
        english[f"{SCHEMATIC_KEY_PREFIX}{build.id}.description"] = build.description
        russian[f"{SCHEMATIC_KEY_PREFIX}{build.id}.name"] = build.name_ru
        russian[f"{SCHEMATIC_KEY_PREFIX}{build.id}.description"] = build.description_ru
    update_lang(LANG / "en_us.json", english)
    update_lang(LANG / "ru_ru.json", russian)
    print(f"{len(builds)} schematics written")


if __name__ == "__main__":
    main()
