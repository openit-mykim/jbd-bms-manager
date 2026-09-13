#!/usr/bin/env python3
from __future__ import annotations

import shutil
import sys
from pathlib import Path

UPSTREAM_COMMIT = "7e3e225a128f6e0d69425b98a2670d8d69594885"


def replace_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def replace_optional(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old in text:
        path.write_text(text.replace(old, new), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: apply-product-overlay.py <OpenJBD source dir>")

    root = Path(sys.argv[1]).resolve()
    if not (root / "app" / "build.gradle.kts").exists():
        raise RuntimeError(f"Not an OpenJBD checkout: {root}")

    repo_root = Path(__file__).resolve().parents[1]
    overlay_root = repo_root / "overlays"

    # 1) Copy files that are fully owned by this product overlay.
    for src in overlay_root.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(overlay_root)
        dst = root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    # 2) Product identity while keeping the upstream Kotlin namespace during the
    # initial derivative phase. applicationId is unique so both apps can coexist.
    gradle = root / "app" / "build.gradle.kts"
    replace_required(gradle, 'applicationId = "com.gytxtx.openjbd"', 'applicationId = "com.openit.jbdbmsmanager"')
    replace_required(gradle, "versionCode = 1", "versionCode = 2")
    replace_required(gradle, 'versionName = "0.1.0"', 'versionName = "0.1.0-alpha.1"')

    # 3) Base English resources: app branding and explicit Korean language entry.
    strings = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    replace_required(strings, '<string name="app_name">OpenJBD MVP</string>', '<string name="app_name">JBD BMS Manager</string>')
    replace_required(
        strings,
        '<string name="setting_language_zh">Simplified Chinese</string>\n    <string name="setting_language_en">English</string>',
        '<string name="setting_language_zh">Simplified Chinese</string>\n    <string name="setting_language_ko">Korean</string>\n    <string name="setting_language_en">English</string>',
    )
    replace_optional(strings, '<string name="about_version">Version 0.1.0</string>', '<string name="about_version">Version 0.1.0-alpha.1</string>')
    replace_optional(
        strings,
        'OpenJBD is a local, account-free Android BLE monitor for JBD / Xiaoxiang BMS devices. It focuses on safely reading battery status without cloud services.',
        'JBD BMS Manager is an unofficial local Android BLE manager for JBD / Xiaoxiang BMS devices, derived from OpenJBD. Core monitoring works without an account or cloud service.',
    )

    # 4) Add Korean to the application's manual language selector.
    app_settings = root / "app" / "src" / "main" / "kotlin" / "com" / "gytxtx" / "openjbd" / "AppSettings.kt"
    replace_required(
        app_settings,
        'const val VALUE_ZH = "zh"\n    const val VALUE_EN = "en"',
        'const val VALUE_ZH = "zh"\n    const val VALUE_KO = "ko"\n    const val VALUE_EN = "en"',
    )
    replace_required(
        app_settings,
        'val locale = if (VALUE_ZH == language) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH',
        'val locale = when (language) {\n            VALUE_ZH -> Locale.SIMPLIFIED_CHINESE\n            VALUE_KO -> Locale.KOREAN\n            else -> Locale.ENGLISH\n        }',
    )

    settings_fragment = root / "app" / "src" / "main" / "kotlin" / "com" / "gytxtx" / "openjbd" / "SettingsFragment.kt"
    replace_required(
        settings_fragment,
        'arrayOf(getString(R.string.setting_language_system), getString(R.string.setting_language_zh), getString(R.string.setting_language_en)),\n                arrayOf(AppSettings.VALUE_SYSTEM, AppSettings.VALUE_ZH, AppSettings.VALUE_EN)',
        'arrayOf(getString(R.string.setting_language_system), getString(R.string.setting_language_ko), getString(R.string.setting_language_zh), getString(R.string.setting_language_en)),\n                arrayOf(AppSettings.VALUE_SYSTEM, AppSettings.VALUE_KO, AppSettings.VALUE_ZH, AppSettings.VALUE_EN)',
    )
    replace_required(
        settings_fragment,
        'private fun languageLabel(value: String) = when (value) { AppSettings.VALUE_ZH -> getString(R.string.setting_language_zh); AppSettings.VALUE_EN -> getString(R.string.setting_language_en); else -> getString(R.string.setting_language_system) }',
        'private fun languageLabel(value: String) = when (value) { AppSettings.VALUE_KO -> getString(R.string.setting_language_ko); AppSettings.VALUE_ZH -> getString(R.string.setting_language_zh); AppSettings.VALUE_EN -> getString(R.string.setting_language_en); else -> getString(R.string.setting_language_system) }',
    )

    # 5) Android 16: remove the obsolete edge-to-edge opt-out from the theme.
    styles = root / "app" / "src" / "main" / "res" / "values" / "styles.xml"
    replace_optional(styles, '        <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>\n', "")

    # 6) Point in-app source link to this derivative project while preserving
    # the upstream attribution/license page.
    for path in (root / "app" / "src" / "main").rglob("*.kt"):
        replace_optional(path, "https://github.com/gytxtx/OpenJBD", "https://github.com/openit-mykim/jbd-bms-manager")

    marker = root / "JBD_BMS_MANAGER_BUILD.txt"
    marker.write_text(
        "JBD BMS Manager alpha overlay\n"
        f"OpenJBD baseline: {UPSTREAM_COMMIT}\n"
        "Changes: Android 16 system-bar insets, Korean localization, product identity.\n",
        encoding="utf-8",
    )

    print(f"Applied JBD BMS Manager overlay to {root}")


if __name__ == "__main__":
    main()
