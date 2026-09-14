#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()

def replace(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"missing expected text in {path}: {old}")
    path.write_text(text.replace(old, new), encoding="utf-8")

build = root / "app" / "build.gradle.kts"
replace(build, "versionCode = 11", "versionCode = 12")
replace(build, 'versionName = "0.1.0-alpha.10"', 'versionName = "0.1.0-alpha.11"')

print("Applied alpha.11 packaging patch")
