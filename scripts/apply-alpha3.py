#!/usr/bin/env python3
from pathlib import Path
import base64
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

# Use the user-supplied chick face image as the launcher icon. The source image
# is only resized/compressed for Android packaging; its composition is unchanged.
drawable_dir = root / "app" / "src" / "main" / "res" / "drawable"
drawable_dir.mkdir(parents=True, exist_ok=True)
vector_icon = drawable_dir / "jbd_app_icon.xml"
if vector_icon.exists():
    vector_icon.unlink()
icon_b64 = (repo_root / "assets" / "jbd_app_icon.webp.b64").read_text(encoding="utf-8").strip()
(drawable_dir / "jbd_app_icon.webp").write_bytes(base64.b64decode(icon_b64))

print("Applied alpha.3 packaging patch")
