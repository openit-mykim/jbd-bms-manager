#!/usr/bin/env python3
"""Automatic design-token check (mobile dev standard 5.7.2 / 5.7.6 / 5.8).

Runs on the *materialized* tree (pinned upstream + overlay) and fails when:
  - a text size outside 11/12/14/16/18/22/28 sp is defined or a Material Headline-based
    OpenJbd text style does not pin its size (documented exception: dashboard_* numerals);
  - a layout margin/padding is off the 4 dp grid;
  - a translatable string has no Korean or Simplified Chinese translation
    (locale-neutral formats, units and English legal texts are exempt).

Usage: check-design-tokens.py <materialized OpenJBD dir>
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ALLOWED_SP = {11, 12, 14, 16, 18, 22, 28}
TEXT_SIZE_EXCEPTION = re.compile(r"^dashboard_")  # docs/mobile-standard-compliance.md §2
LOCALE_NEUTRAL = re.compile(r"^(format_|placeholder_|license_item_|license_detail_|unit_|about_version$)")
TRANSLATIONS = ("values-ko", "values-zh-rCN")
SPACING_ATTR = re.compile(r'android:(?:layout_)?(?:margin|padding)\w*="([^"]+)"')


def dimens(res: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in (res / "values").glob("*.xml"):
        for name, value in re.findall(r'<dimen name="([^"]+)">\s*([^<]+?)\s*</dimen>', path.read_text("utf-8")):
            values[name] = value
    return values


def strings(folder: Path) -> dict[str, bool]:
    found: dict[str, bool] = {}
    for path in folder.glob("*.xml"):
        for name, attrs in re.findall(r'<string name="([^"]+)"([^>]*)>', path.read_text("utf-8")):
            found[name] = 'translatable="false"' not in attrs
    return found


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    res = Path(sys.argv[1]).resolve() / "app" / "src" / "main" / "res"
    problems: list[str] = []
    dimen_values = dimens(res)

    # 5.7.2 text sizes
    for name, value in dimen_values.items():
        if value.endswith("sp") and not TEXT_SIZE_EXCEPTION.match(name):
            size = float(value[:-2])
            if size not in ALLOWED_SP:
                problems.append(f"text size dimen {name}={value} is outside {sorted(ALLOWED_SP)}")
    for path in list(res.glob("layout*/*.xml")) + list(res.glob("values*/styles*.xml")):
        for value in re.findall(r'textSize"?[=>]\s*"?(\d+(?:\.\d+)?)sp', path.read_text("utf-8")):
            if float(value) not in ALLOWED_SP:
                problems.append(f"{path.relative_to(res)}: literal textSize {value}sp")
    styles = (res / "values" / "styles.xml").read_text("utf-8")
    for name, parent, body in re.findall(
        r'<style name="(TextAppearance\.OpenJbd\.[^"]+)" parent="TextAppearance\.MaterialComponents\.(Headline\d)">(.*?)</style>',
        styles,
        re.S,
    ):
        if "android:textSize" not in body:
            problems.append(f"{name} inherits {parent} size without pinning a scale size")

    # 5.7.6 spacing grid
    for path in res.glob("layout*/*.xml"):
        for value in SPACING_ATTR.findall(path.read_text("utf-8")):
            if value.startswith("@dimen/"):
                value = dimen_values.get(value[len("@dimen/"):], value)
            match = re.fullmatch(r"(-?\d+)dp", value)
            if match and int(match.group(1)) % 4 != 0:
                problems.append(f"{path.relative_to(res)}: spacing {value} is off the 4 dp grid")

    # 5.8 translations
    base = strings(res / "values")
    for folder in TRANSLATIONS:
        translated = strings(res / folder)
        missing = [k for k, translatable in base.items()
                   if translatable and not LOCALE_NEUTRAL.match(k) and k not in translated]
        if missing:
            problems.append(f"{folder}: {len(missing)} untranslated strings, e.g. {', '.join(missing[:5])}")

    for problem in problems:
        print(f"FAIL {problem}")
    print(f"design tokens: {'OK' if not problems else f'{len(problems)} problem(s)'}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
