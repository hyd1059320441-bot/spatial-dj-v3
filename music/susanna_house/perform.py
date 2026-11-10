#!/usr/bin/env python3
"""Locate live controls and record the public-domain Oh! Susanna melody as House."""
from pathlib import Path
import runpy
import sys

HERE = Path(__file__).resolve().parent
sys.argv[1:1] = ["--score", str(HERE / "score.json")]
runpy.run_path(str(HERE.parent / "touch_house" / "perform.py"), run_name="__main__")
