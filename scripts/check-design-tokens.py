#!/usr/bin/env python3
"""Automatic design-token check (mobile dev standard 5.7.2 / 5.7.6 / 5.8).

Runs on the *materialized* tree (pinned upstream + overlay) and fails when:
  - a text size outside 11/12/14/16/18/22/28 sp is defined or a Material Headline-based
    OpenJbd text style does not pin its size (documented exception: dashboard_* numerals);
  - a typography step lacks its line height (1.4-1.6x size) or letter spacing (0 to -0.2 sp),
    an app text appearance is not pinned to one step (size + line height + letter spacing),
    or a layout / Kotlin view uses a text appearance outside the validated set or ad-hoc
    line spacing (documented exception: TextAppearance.OpenJbd.Dashboard*);
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
TEXT_APPEARANCE_EXCEPTION = re.compile(r"^TextAppearance\.OpenJbd\.Dashboard")
LINE_HEIGHT_RATIO = (1.4, 1.6)
MAX_NEGATIVE_TRACKING_SP = 0.2  # standard 5.7.2: Korean letter spacing 0 to -0.2
TRANSLATIONS = ("values-ko", "values-zh-rCN")
STYLE = re.compile(r'<style name="([^"]+)"(?:\s+parent="([^"]*)")?\s*(?:/>|>(.*?)</style>)', re.S)
ITEM = re.compile(r'<item name="([^"]+)">\s*([^<]*?)\s*</item>')
SPACING_ATTR = re.compile(r'android:(?:layout_)?(?:margin|padding)\w*="([^"]+)"')


def dimens(res: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in (res / "values").glob("*.xml"):
        for name, value in re.findall(r'<dimen name="([^"]+)">\s*([^<]+?)\s*</dimen>', path.read_text("utf-8")):
            values[name] = value
    return values


def float_dimens(res: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in (res / "values").glob("*.xml"):
        for name, value in re.findall(
            r'<item name="([^"]+)"[^>]*type="dimen"[^>]*>\s*([^<]+?)\s*</item>', path.read_text("utf-8")
        ):
            values[name] = value
    return values


def text_appearances(res: Path) -> dict[str, tuple[str, dict[str, str]]]:
    """OpenJbd text appearances in values/ -> (parent, own items)."""
    found: dict[str, tuple[str, dict[str, str]]] = {}
    for path in (res / "values").glob("*.xml"):
        for name, parent, body in STYLE.findall(path.read_text("utf-8")):
            if name.startswith("TextAppearance.OpenJbd."):
                found[name] = (parent, dict(ITEM.findall(body or "")))
    return found


def resolved_items(name: str, styles: dict[str, tuple[str, dict[str, str]]]) -> dict[str, str]:
    parent, items = styles[name]
    inherited = resolved_items(parent, styles) if parent in styles else {}
    return {**inherited, **items}


def check_typography(res: Path, dimen_values: dict[str, str], problems: list[str]) -> None:
    trackings = float_dimens(res)
    steps = []
    for name, value in dimen_values.items():
        if not re.fullmatch(r"text_[a-z]+", name) or not value.endswith("sp"):
            continue
        steps.append(name)
        size = float(value[:-2])
        line = dimen_values.get(f"{name}_line", "")
        if not line.endswith("sp"):
            problems.append(f"typography step {name} has no {name}_line in sp")
        elif not LINE_HEIGHT_RATIO[0] <= float(line[:-2]) / size <= LINE_HEIGHT_RATIO[1]:
            problems.append(f"typography step {name}: line height {line} is not 1.4-1.6x {value}")
        tracking = trackings.get(f"{name}_tracking")
        if tracking is None:
            problems.append(f"typography step {name} has no {name}_tracking (letter spacing)")
        elif not -MAX_NEGATIVE_TRACKING_SP / size <= float(tracking) <= 0:
            problems.append(f"typography step {name}: letter spacing {tracking}em is outside 0 to -0.2sp")

    styles = text_appearances(res)
    validated: set[str] = set()
    for name in styles:
        if TEXT_APPEARANCE_EXCEPTION.match(name):
            validated.add(name)
            continue
        items = resolved_items(name, styles)
        size = items.get("android:textSize", "")
        step = size[len("@dimen/"):] if size.startswith("@dimen/") else ""
        expected = {
            "android:textSize": f"@dimen/{step}",
            "android:lineHeight": f"@dimen/{step}_line",
            "lineHeight": f"@dimen/{step}_line",
            "android:letterSpacing": f"@dimen/{step}_tracking",
        }
        wrong = [attr for attr, value in expected.items() if step not in steps or items.get(attr) != value]
        if wrong:
            problems.append(f"{name} is not pinned to one typography step ({', '.join(wrong)})")
        else:
            validated.add(name)

    for path in res.glob("layout*/*.xml"):
        text = path.read_text("utf-8")
        for ref in sorted(set(re.findall(r'android:textAppearance="([^"]+)"', text))):
            if not (ref.startswith("@style/") and ref[len("@style/"):] in validated):
                problems.append(f"{path.relative_to(res)}: text appearance {ref} has no typography step")
        if re.search(r"android:lineSpacing(?:Extra|Multiplier)=", text):
            problems.append(f"{path.relative_to(res)}: ad-hoc lineSpacing; use the step's line height")
    for path in (res.parent / "kotlin").rglob("*.kt"):
        for ref in re.findall(r"setTextAppearance\((?:[^,()]+,\s*)?R\.style\.(\w+)\)", path.read_text("utf-8")):
            if ref.replace("_", ".") not in validated:
                problems.append(f"{path.relative_to(res.parent)}: text appearance {ref} has no typography step")


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
        # *_line dimens are line heights in sp (checked per step below), not text sizes.
        if value.endswith("sp") and not TEXT_SIZE_EXCEPTION.match(name) and not name.endswith("_line"):
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

    # 5.7.2 line height and letter spacing per step
    check_typography(res, dimen_values, problems)

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
