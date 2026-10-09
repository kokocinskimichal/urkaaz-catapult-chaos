#!/usr/bin/env python3
"""Generate a deterministic asset inventory for the migration workspace."""

from __future__ import annotations

import csv
import struct
import sys
from pathlib import Path


def png_metadata(path: Path) -> tuple[str, str]:
    try:
        with path.open("rb") as stream:
            if stream.read(8) != b"\x89PNG\r\n\x1a\n":
                return "", ""
            length = struct.unpack(">I", stream.read(4))[0]
            chunk = stream.read(4)
            if chunk != b"IHDR" or length < 13:
                return "", ""
            width, height, _, color_type, _, _, _ = struct.unpack(
                ">IIBBBBB", stream.read(13)
            )
            alpha = "yes" if color_type in {4, 6} else "no"
            return f"{width}x{height}", alpha
    except (OSError, struct.error):
        return "", ""


def classify(relative_path: str) -> tuple[str, str, str]:
    name = Path(relative_path).name.lower()
    path = relative_path.lower()
    if "terrain" in name or "terrain" in path:
        category = "terrain"
    elif "fortress" in name or "objective" in name or "castle" in name:
        category = "fortresses"
    elif "catapult" in name:
        category = "catapults"
    elif any(token in name for token in ("projectile", "rock_", "bomb_", "barrel_", "cauldron_", "fire_rain")):
        category = "projectiles"
    elif any(token in name for token in ("spell", "trap", "bastion", "banner", "skin", "haste", "heal")):
        category = "spells"
    elif any(token in name for token in ("defender", "sapper", "demolisher", "goblin", "unit_")):
        category = "units"
    elif any(token in name for token in ("icon", "button", "menu", "avatar", "hud", "ammo")):
        category = "ui"
    elif any(token in name for token in ("wind", "impact", "effect", "smoke", "spark")):
        category = "effects"
    elif "tutorial" in name or "tutorial" in path:
        category = "tutorial"
    elif Path(name).suffix in {".ttf", ".otf"}:
        category = "fonts"
    elif Path(name).suffix in {".mp3", ".wav", ".ogg"}:
        category = "audio"
    elif "background" in name or "backdrop" in name:
        category = "backgrounds"
    else:
        category = "unclassified"

    if "drawable-nodpi" in path or "assets_src" in path:
        usage = "runtime_candidate"
    elif "layout" in path or "values" in path or "mipmap" in path:
        usage = "android_resource"
    else:
        usage = "source_or_other"

    if any(token in name for token in ("preview", "contact", "debug", "temp", "copy")):
        decision = "review"
    elif category == "unclassified":
        decision = "review"
    elif usage == "runtime_candidate":
        decision = "copy_or_process"
    else:
        decision = "recreate_or_review"
    return category, usage, decision


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: inventory_assets.py OLD_PROJECT_ROOT OUTPUT_CSV", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    output = Path(sys.argv[2]).resolve()
    candidates = []
    for base in (root / "app" / "src" / "main" / "res", root / "assets_src"):
        if base.exists():
            candidates.extend(path for path in base.rglob("*") if path.is_file())

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(
            [
                "source_path",
                "category",
                "file_type",
                "format",
                "resolution",
                "alpha",
                "density_or_scaling",
                "runtime_usage",
                "decision",
            ]
        )
        for path in sorted(candidates):
            relative = path.relative_to(root).as_posix()
            suffix = path.suffix.lower().lstrip(".") or "none"
            category, usage, decision = classify(relative)
            resolution, alpha = png_metadata(path) if suffix == "png" else ("", "")
            density = "nodpi" if "drawable-nodpi" in relative else "android_default"
            writer.writerow(
                [
                    relative,
                    category,
                    "image" if suffix in {"png", "jpg", "jpeg", "webp"} else "resource_or_other",
                    suffix,
                    resolution,
                    alpha,
                    density,
                    usage,
                    decision,
                ]
            )

    print(f"files={len(candidates)}")
    print(f"output={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
