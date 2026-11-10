#!/usr/bin/env python3
"""Prepare the SDK's supported RGB11F neutral IBL; intensity is set in ClubLighting."""
from pathlib import Path
import hashlib
import shutil

target = Path(__file__).resolve().parents[1] / "app/src/main/assets/club/night-ambient.ktx"
target.parent.mkdir(parents=True, exist_ok=True)
source = Path("/Users/bytedance/Library/PICO/sdk/6.1/editor/Spatial Editor.app/"
              "Contents/MacOS/resources/builtin/assets/env/SystemIbl_ibl_brightness.ktx")
# B3110 rejects generic RGBA16F KTX; this SDK file includes RGB11F mips and SH data.
shutil.copyfile(source, target)
print(f"{target}: {target.stat().st_size} bytes, RGB11F + SH, "
      f"sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")
