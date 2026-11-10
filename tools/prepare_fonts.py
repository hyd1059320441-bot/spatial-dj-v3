#!/usr/bin/env python3
"""Freeze/subset OFL Noto Sans SC for offline, consistent Android typography.

Usage: python3 tools/prepare_fonts.py /path/to/NotoSansSC[wght].ttf /path/to/OFL.txt
Requires fonttools. The upstream files are downloaded separately, never at app runtime.
"""
import hashlib
import json
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf"
RANGES = [(0x20, 0x24F), (0x2000, 0x206F), (0x3000, 0x303F),
          (0x3400, 0x9FFF), (0xFE00, 0xFE0F), (0xFF00, 0xFFEF)]


def main():
    source, license_path = map(Path, sys.argv[1:3])
    license_text = license_path.read_text(encoding="utf-8")
    if "SIL OPEN FONT LICENSE Version 1.1" not in license_text:
        raise ValueError("Expected the upstream OFL 1.1 license")
    target = ROOT / "app/src/main/res/font"
    target.mkdir(parents=True, exist_ok=True)
    legal = ROOT / "app/src/main/assets/licenses"
    legal.mkdir(parents=True, exist_ok=True)
    (legal / "LoopSansSC-OFL.txt").write_text(license_text, encoding="utf-8")
    source_font = TTFont(source)
    available = set(source_font.getBestCmap())
    wanted = {cp for start, end in RANGES for cp in range(start, end + 1)} & available
    # Check every Chinese/Latin/punctuation character in app UI sources against the font.
    ui_chars = set()
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        ui_chars.update(c for c in path.read_text(encoding="utf-8")
                        if ord(c) >= 0x20 and (ord(c) < 0x7F or ord(c) >= 0x3000))
    missing = sorted(c for c in ui_chars if ord(c) not in wanted)
    if missing:
        raise ValueError(f"Missing app characters: {missing}")
    artifacts = []
    for weight, style in [(400, "Regular"), (600, "SemiBold")]:
        font = instantiateVariableFont(source_font, {"wght": weight}, inplace=False)
        options = subset.Options()
        options.name_IDs = ["*"]
        options.name_legacy = True
        options.name_languages = ["*"]
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=wanted)
        sub.subset(font)
        names = {1: "Loop Sans SC", 2: style, 3: f"LoopSansSC-{style}-1",
                 4: f"Loop Sans SC {style}", 6: f"LoopSansSC-{style}",
                 16: "Loop Sans SC", 17: style}
        for name_id, text in names.items():
            font["name"].removeNames(nameID=name_id)
            font["name"].setName(text, name_id, 3, 1, 0x409)
        file = target / f"loop_sans_sc_{'regular' if weight == 400 else 'semibold'}.ttf"
        font.save(file)
        check = TTFont(file)
        assert "fvar" not in check and check["OS/2"].usWeightClass == weight
        assert all(ord(c) in check.getBestCmap() for c in ui_chars)
        artifacts.append({"file": str(file.relative_to(ROOT)), "weight": weight,
                          "glyphs": len(check.getBestCmap()), "bytes": file.stat().st_size,
                          "sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
        print(artifacts[-1], flush=True)
    report = {"upstream": SOURCE_URL, "license": "SIL OFL 1.1",
              "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "uiCharactersChecked": len(ui_chars), "fonts": artifacts}
    (ROOT / "tools/font-manifest.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
