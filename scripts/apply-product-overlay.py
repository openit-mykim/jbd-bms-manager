#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


VERSION = read_properties(REPO_ROOT / "version.properties")
UPSTREAM_COMMIT = VERSION["upstreamCommit"]
VERSION_CODE = int(VERSION["versionCode"])
VERSION_NAME = VERSION["versionName"]


def replace_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def replace_line_required(path: Path, pattern: str, new: str) -> None:
    """Replace exactly one whole-line regex match; fails loudly when upstream changed."""
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, new, text, count=0, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"Expected exactly one line matching {pattern!r} in {path}, found {count}")
    path.write_text(updated, encoding="utf-8")


def replace_optional(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old in text:
        path.write_text(text.replace(old, new), encoding="utf-8")


# Mobile dev standard 5.7.6: spacing uses the 4 dp grid (4/8/12/16/24/32) and the screen's
# side margin is 16. Upstream dimens off that grid are remapped in every layout, so upstream
# files are not replaced wholesale and the rule lives in one place.
SPACING_DIMEN_MAP = {
    "space_2": "space_4",
    "space_6": "space_8",
    "space_9": "space_8",
    "space_10": "space_12",
    "space_14": "space_16",
    "space_18": "space_16",
}
LITERAL_SPACING_MAP = {"2dp": "4dp", "6dp": "8dp", "10dp": "12dp", "14dp": "16dp", "18dp": "16dp", "20dp": "16dp"}


def normalize_layout_spacing(layout_dir: Path) -> None:
    dimen_pattern = re.compile(r"@dimen/(" + "|".join(SPACING_DIMEN_MAP) + r')"')
    literal_pattern = re.compile(
        r'(android:(?:layout_)?(?:margin|padding)\w*=")(' + "|".join(LITERAL_SPACING_MAP) + r')"'
    )
    for layout in layout_dir.glob("*.xml"):
        text = layout.read_text(encoding="utf-8")
        updated = dimen_pattern.sub(lambda m: f'@dimen/{SPACING_DIMEN_MAP[m.group(1)]}"', text)
        updated = literal_pattern.sub(lambda m: f'{m.group(1)}{LITERAL_SPACING_MAP[m.group(2)]}"', updated)
        if updated != text:
            layout.write_text(updated, encoding="utf-8")


# Mobile dev standard 5.7.2: the typography step (values/typography.xml) of every upstream
# OpenJbd text appearance. Styles inheriting from one of these (DeviceStatus, StatusValue)
# inherit its step. Dashboard* styles are the documented large-numeral exception.
TEXT_APPEARANCE_STEPS = {
    "EmptyTitle": "title",
    "EmptyBody": "body",
    "Status": "label",
    "MetricLabel": "caption",
    "MetricValue": "heading",
    "PrimaryMetricValue": "display",
    "Supporting": "caption",
    "CellLabel": "label",
    "CellValue": "body",
    "AboutTitle": "title",
    "AboutBody": "body",
    "ListItemTitle": "body",
    "ListItemSubtitle": "label",
}


def pin_text_appearance_steps(styles: Path) -> None:
    text = styles.read_text(encoding="utf-8")
    if "xmlns:tools=" not in text:
        if text.count("<resources>") != 1:
            raise RuntimeError(f"Expected one bare <resources> element in {styles}")
        text = text.replace("<resources>", '<resources xmlns:tools="http://schemas.android.com/tools">')
    for style, step in TEXT_APPEARANCE_STEPS.items():
        pattern = re.compile(r'(<style name="TextAppearance\.OpenJbd\.' + re.escape(style) + r'" parent="[^"]+")(\s*/>|>)')
        matches = pattern.findall(text)
        if len(matches) != 1:
            raise RuntimeError(f"Expected exactly one TextAppearance.OpenJbd.{style} in {styles}, found {len(matches)}")
        items = (
            f'\n        <item name="android:textSize">@dimen/text_{step}</item>'
            # android:lineHeight is API 28+ on plain TextView; MaterialTextView applies it (and the
            # Material lineHeight attr below) on every API level, so lint's NewApi is suppressed.
            f'\n        <item name="android:lineHeight" tools:targetApi="28">@dimen/text_{step}_line</item>'
            f'\n        <item name="lineHeight">@dimen/text_{step}_line</item>'
            f'\n        <item name="android:letterSpacing">@dimen/text_{step}_tracking</item>'
        )
        # A self-closing style (e.g. Status) is opened up so it can carry the items.
        text = pattern.sub(
            lambda m: m.group(1) + ">" + items + ("\n    </style>" if m.group(2).strip() == "/>" else ""),
            text,
        )
    styles.write_text(text, encoding="utf-8")


def add_upstream_attribution(root: Path) -> None:
    upstream_url = f"https://github.com/{VERSION['upstreamRepo']}"
    layout = root / "app" / "src" / "main" / "res" / "layout" / "activity_about.xml"
    replace_required(
        layout,
        '                <include\n                    android:id="@+id/item_source_code"\n                    layout="@layout/row_about_list_item" />\n',
        '                <include\n                    android:id="@+id/item_source_code"\n                    layout="@layout/row_about_list_item" />\n\n'
        '                <include\n                    android:id="@+id/item_upstream_source"\n                    layout="@layout/row_about_list_item" />\n',
    )
    about = root / "app" / "src" / "main" / "kotlin" / "com" / "gytxtx" / "openjbd" / "AboutActivity.kt"
    replace_required(
        about,
        "            getString(R.string.about_item_source_code),\n            null,\n",
        '            getString(R.string.about_item_source_code),\n            REPOSITORY_URL.removePrefix("https://"),\n',
    )
    replace_required(
        about,
        "        sourceCodeItem.setOnClickListener {\n"
        "            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))\n"
        "        }\n",
        "        sourceCodeItem.setOnClickListener {\n"
        "            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))\n"
        "        }\n\n"
        "        val upstreamItem = findViewById<View>(R.id.item_upstream_source)\n"
        "        bindListItem(\n"
        "            upstreamItem,\n"
        "            R.drawable.ic_github_24,\n"
        "            getString(R.string.about_item_upstream_source),\n"
        "            getString(R.string.about_item_upstream_source_subtitle),\n"
        "            true\n"
        "        )\n"
        "        upstreamItem.setOnClickListener {\n"
        "            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPSTREAM_REPOSITORY_URL)))\n"
        "        }\n",
    )
    replace_line_required(
        about,
        r'^(\s*)private const val REPOSITORY_URL = "https://github\.com/openit-mykim/jbd-bms-manager"$',
        r'\g<0>' + f'\n\\g<1>private const val UPSTREAM_REPOSITORY_URL = "{upstream_url}"',
    )


GRADLE_ANDROID_TAIL = """    buildFeatures {
        compose = true
    }
}
"""
GRADLE_ANDROID_TAIL_WITH_SIGNING = """    buildFeatures {
        compose = true
    }

    val releaseKeystore = System.getenv("JBD_RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "PKCS12"
                storePassword = System.getenv("JBD_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("JBD_RELEASE_KEY_ALIAS")
                // PKCS12 keystores use the store password for the key as well.
                keyPassword = System.getenv("JBD_RELEASE_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        // Robolectric screen tests (issue #9) need merged resources and the manifest.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Upstream OpenJBD findings are frozen in the baseline; new findings fail the build.
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
    }
}
"""


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: apply-product-overlay.py <OpenJBD source dir>")

    root = Path(sys.argv[1]).resolve()
    if not (root / "app" / "build.gradle.kts").exists():
        raise RuntimeError(f"Not an OpenJBD checkout: {root}")

    repo_root = Path(__file__).resolve().parents[1]
    overlay_root = repo_root / "overlays"

    for src in overlay_root.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(overlay_root)
        dst = root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    gradle = root / "app" / "build.gradle.kts"
    replace_required(gradle, 'applicationId = "com.gytxtx.openjbd"', 'applicationId = "com.openit.jbdbmsmanager"')
    # Whole-line matches: a substring match of "versionCode = 1" would also hit "= 16".
    replace_line_required(gradle, r"^(\s*)versionCode = \d+$", rf"\g<1>versionCode = {VERSION_CODE}")
    replace_line_required(gradle, r'^(\s*)versionName = "[^"]*"$', rf'\g<1>versionName = "{VERSION_NAME}"')
    # Fixed release signing (issue #9). The keystore never lives in the repository: CI decodes
    # it from GitHub Secrets and passes paths/passwords through the environment. Without
    # JBD_RELEASE_KEYSTORE_FILE the release variant stays unsigned, and the release guard blocks it.
    replace_required(gradle, GRADLE_ANDROID_TAIL, GRADLE_ANDROID_TAIL_WITH_SIGNING)
    # Screen tests run on the JVM with Robolectric + Hilt testing (issue #9).
    replace_required(
        gradle,
        "    testImplementation(libs.kotlinx.coroutines.test)\n",
        "    testImplementation(libs.kotlinx.coroutines.test)\n"
        '    testImplementation("org.robolectric:robolectric:4.18")\n'
        '    testImplementation("androidx.test:core-ktx:1.7.0")\n'
        '    testImplementation("androidx.test.ext:junit-ktx:1.3.0")\n'
        '    testImplementation("com.google.dagger:hilt-android-testing:2.60.1")\n'
        "    kspTest(libs.hilt.compiler)\n",
    )

    strings = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    replace_required(strings, '<string name="app_name">OpenJBD MVP</string>', '<string name="app_name">JBD BMS Manager</string>')
    replace_required(
        strings,
        '<string name="setting_language_zh">Simplified Chinese</string>\n    <string name="setting_language_en">English</string>',
        '<string name="setting_language_zh">Simplified Chinese</string>\n    <string name="setting_language_ko">Korean</string>\n    <string name="setting_language_en">English</string>',
    )
    replace_optional(
        strings,
        'OpenJBD is a local, account-free Android BLE monitor for JBD / Xiaoxiang BMS devices. It focuses on safely reading battery status without cloud services.',
        'JBD BMS Manager is an unofficial local Android BLE manager for JBD / Xiaoxiang BMS devices, derived from OpenJBD. Core monitoring works without an account or cloud service.',
    )

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

    manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
    replace_required(manifest, 'android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/jbd_app_icon"')
    replace_required(manifest, 'android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/jbd_app_icon"')
    # The About header showed upstream's placeholder launcher mipmap instead of the product icon.
    replace_required(
        root / "app" / "src" / "main" / "res" / "layout" / "activity_about.xml",
        'android:src="@mipmap/ic_launcher"',
        'android:src="@drawable/jbd_app_icon"',
    )

    drawable_dir = root / "app" / "src" / "main" / "res" / "drawable"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    for stale in (drawable_dir / "jbd_app_icon.xml", drawable_dir / "jbd_app_icon.webp"):
        if stale.exists():
            stale.unlink()

    icon_values: list[int] = []
    for idx in range(1, 6):
        text = (repo_root / "assets" / f"icon.part{idx}.txt").read_text(encoding="utf-8").strip()
        icon_values.extend(int(value) for value in text.split(",") if value)
    (drawable_dir / "jbd_app_icon.png").write_bytes(bytes(icon_values))

    styles = root / "app" / "src" / "main" / "res" / "values" / "styles.xml"
    # Core Android 16 fix: removing the edge-to-edge opt-out must never silently no-op.
    replace_required(styles, '        <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>\n', "")

    # Mobile dev standard 5.7.2: only 11/12/14/16/18/22/28 sp, each with its line height and
    # letter spacing (values/typography.xml). Every upstream app text appearance is pinned to
    # one step, which also replaces Material parents outside the list (Headline3 48,
    # Headline5 24, Headline6 20). Dashboard numerals are the documented exception.
    pin_text_appearance_steps(styles)
    # Section titles and the settings menu row used Material type-scale styles directly.
    replace_required(
        styles,
        '<item name="android:textAppearance">@style/TextAppearance.MaterialComponents.Subtitle1</item>',
        '<item name="android:textAppearance">@style/TextAppearance.OpenJbd.Subtitle1</item>',
    )
    layout_dir = root / "app" / "src" / "main" / "res" / "layout"
    replace_required(
        layout_dir / "row_setting_menu_item.xml",
        'android:textAppearance="@style/TextAppearance.MaterialComponents.Body1"',
        'android:textAppearance="@style/TextAppearance.OpenJbd.Body1"',
    )
    # The About body's ad-hoc extra spacing would fight the step's line height.
    replace_required(layout_dir / "activity_about.xml", '                    android:lineSpacingExtra="3dp"\n', "")

    normalize_layout_spacing(root / "app" / "src" / "main" / "res" / "layout")

    # Upstream launched repeatOnLifecycle from onStart, which stacks one more collector on
    # every start (lint RepeatOnLifecycleWrongUsage). Collect once from onCreate / onViewCreated.
    kotlin_dir = root / "app" / "src" / "main" / "kotlin" / "com" / "gytxtx" / "openjbd"
    dashboard = kotlin_dir / "DashboardActivity.kt"
    replace_required(
        dashboard,
        """        SystemBars.applyFullscreen(this)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { render(it) }
            }
        }
""",
        "        SystemBars.applyFullscreen(this)\n",
    )
    replace_required(
        dashboard,
        "        render(repository.getSnapshot())\n    }\n",
        """        render(repository.getSnapshot())
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { render(it) }
            }
        }
    }
""",
    )
    settings = kotlin_dir / "SettingsFragment.kt"
    replace_required(
        settings,
        """    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { connected = it.connected }
            }
        }
    }

    override fun onStop() = super.onStop()
""",
        "",
    )
    replace_required(
        settings,
        "        connected = repository.getSnapshot().connected\n        renderSettingsRows()\n    }\n",
        """        connected = repository.getSnapshot().connected
        renderSettingsRows()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.uiState.collect { connected = it.connected }
            }
        }
    }
""",
    )

    # Upstream's Parameters page was split into Detail (user) and Control diagnostics
    # (technician); nothing references it any more, so it is not shipped.
    for dead in (
        root / "app" / "src" / "main" / "kotlin" / "com" / "gytxtx" / "openjbd" / "ParametersFragment.kt",
        root / "app" / "src" / "main" / "res" / "layout" / "fragment_parameters.xml",
    ):
        if not dead.exists():
            raise RuntimeError(f"Expected upstream file not found (upstream changed?): {dead}")
        dead.unlink()
    # The upstream resource test pins row_setting_item's vertical padding to space_10;
    # the grid remap above makes it space_12, so the expectation moves with it.
    resource_test = root / "app" / "src" / "test" / "kotlin" / "com" / "gytxtx" / "openjbd" / "ui" / "Material2ResourceTest.kt"
    replace_required(
        resource_test,
        '"@dimen/touch_target_min",\n            "@dimen/space_16",\n            "@dimen/space_10",\n            "@dimen/space_16",\n            "@dimen/space_10"',
        '"@dimen/touch_target_min",\n            "@dimen/space_16",\n            "@dimen/space_12",\n            "@dimen/space_16",\n            "@dimen/space_12"',
    )

    # Offer Korean in the Android 13+ per-app language picker.
    locales = root / "app" / "src" / "main" / "res" / "xml" / "locales_config.xml"
    replace_required(
        locales,
        '    <locale android:name="en" />\n',
        '    <locale android:name="en" />\n    <locale android:name="ko" />\n',
    )

    for path in (root / "app" / "src" / "main").rglob("*.kt"):
        replace_optional(path, "https://github.com/gytxtx/OpenJBD", "https://github.com/openit-mykim/jbd-bms-manager")

    # The bulk replacement above points "Source code" at this project; the original OpenJBD
    # project (MIT, Copyright (c) 2026 KFACBT) keeps its own attribution link right below it.
    # Applied after the bulk replacement so the upstream URL is not rewritten.
    add_upstream_attribution(root)

    marker = root / "JBD_BMS_MANAGER_BUILD.txt"
    marker.write_text(
        "JBD BMS Manager product overlay\n"
        f"OpenJBD baseline: {UPSTREAM_COMMIT}\n"
        "Changes: Android 16 system-bar insets, Korean localization, product identity.\n",
        encoding="utf-8",
    )

    print(f"Applied JBD BMS Manager overlay to {root}")


if __name__ == "__main__":
    main()
