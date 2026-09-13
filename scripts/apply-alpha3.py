#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
repo_root = Path(__file__).resolve().parents[1]

def replace(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"missing expected text in {path}: {old}")
    path.write_text(text.replace(old, new), encoding="utf-8")

build = root / "app" / "build.gradle.kts"
replace(build, "versionCode = 3", "versionCode = 4")
replace(build, 'versionName = "0.1.0-alpha.2"', 'versionName = "0.1.0-alpha.3"')

manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
replace(manifest, 'android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/jbd_app_icon"')
replace(manifest, 'android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/jbd_app_icon"')

drawable_dir = root / "app" / "src" / "main" / "res" / "drawable"
drawable_dir.mkdir(parents=True, exist_ok=True)
for stale in (drawable_dir / "jbd_app_icon.xml", drawable_dir / "jbd_app_icon.webp"):
    if stale.exists():
        stale.unlink()

values = []
for idx in range(1, 6):
    text = (repo_root / "assets" / f"icon.part{idx}.txt").read_text(encoding="utf-8").strip()
    values.extend(int(v) for v in text.split(",") if v)
(drawable_dir / "jbd_app_icon.png").write_bytes(bytes(values))

print("Applied PNG launcher icon")
