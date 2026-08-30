#!/usr/bin/env python3
"""Normalize every compiled native HCF animator onto HcfMotionSystem.

This is intentionally a build transform rather than a second runtime animation layer.
It rewrites the existing animation owners in-place inside the CI checkout so Startup,
MainActivity, Settings, Safe Mode, widget/native pages, recovery UI, dialogs and helper
classes keep their own state logic while sharing one duration/interpolator policy.
"""

from __future__ import annotations

import pathlib
import re
import sys
from dataclasses import dataclass


@dataclass
class Stats:
    files_scanned: int = 0
    files_changed: int = 0
    durations: int = 0
    delays: int = 0
    repeats: int = 0
    interpolators: int = 0
    motion_duration_methods: int = 0


def replace_interpolators(text: str, stats: Stats) -> str:
    patterns = [
        (r"new\s+(?:android\.view\.animation\.)?AccelerateDecelerateInterpolator\s*\(\s*\)",
         "HcfMotionSystem.standard()"),
        (r"new\s+(?:android\.view\.animation\.)?DecelerateInterpolator\s*\([^)]*\)",
         "HcfMotionSystem.decelerate()"),
        (r"new\s+(?:android\.view\.animation\.)?AccelerateInterpolator\s*\([^)]*\)",
         "HcfMotionSystem.accelerate()"),
        (r"new\s+(?:android\.view\.animation\.)?OvershootInterpolator\s*\([^)]*\)",
         "HcfMotionSystem.emphasized()"),
        (r"new\s+(?:android\.view\.animation\.)?AnticipateOvershootInterpolator\s*\([^)]*\)",
         "HcfMotionSystem.emphasized()"),
        (r"new\s+(?:android\.view\.animation\.)?BounceInterpolator\s*\(\s*\)",
         "HcfMotionSystem.emphasized()"),
        (r"new\s+(?:android\.view\.animation\.)?LinearInterpolator\s*\(\s*\)",
         "HcfMotionSystem.linear()"),
        # Existing hand-authored cubic curves are deliberately collapsed into the
        # app-wide emphasized curve. HcfMotionSystem.java itself is excluded.
        (r"new\s+(?:android\.view\.animation\.)?PathInterpolator\s*\([^)]*\)",
         "HcfMotionSystem.emphasized()"),
    ]
    for pattern, replacement in patterns:
        text, count = re.subn(pattern, replacement, text)
        stats.interpolators += count
    return text


def wrap_balanced_call(text: str, method: str, wrapper: str, stats_field: str,
                       stats: Stats, skip_contains: tuple[str, ...] = ()) -> str:
    token = "." + method + "("
    cursor = 0
    out: list[str] = []
    while True:
        start = text.find(token, cursor)
        if start < 0:
            out.append(text[cursor:])
            break

        out.append(text[cursor:start + len(token)])
        expr_start = start + len(token)
        depth = 1
        i = expr_start
        in_string = False
        in_char = False
        escaped = False
        while i < len(text) and depth:
            ch = text[i]
            if escaped:
                escaped = False
            elif ch == "\\" and (in_string or in_char):
                escaped = True
            elif ch == '"' and not in_char:
                in_string = not in_string
            elif ch == "'" and not in_string:
                in_char = not in_char
            elif not in_string and not in_char:
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
            i += 1

        if depth != 0:
            out.append(text[expr_start:])
            break

        expr_end = i - 1
        expr = text[expr_start:expr_end]
        stripped = expr.strip()
        should_skip = (
            not stripped
            or wrapper in stripped
            or any(marker in stripped for marker in skip_contains)
        )
        if should_skip:
            out.append(expr)
        else:
            out.append(f"{wrapper}({expr})")
            setattr(stats, stats_field, getattr(stats, stats_field) + 1)
        out.append(")")
        cursor = i
    return "".join(out)


def normalize_java(path: pathlib.Path, stats: Stats) -> None:
    stats.files_scanned += 1
    text = path.read_text(encoding="utf-8")
    original = text

    # The policy class must not rewrite itself into recursive calls.
    if path.name == "HcfMotionSystem.java":
        return

    # MainActivity already had a local performance duration helper. Redirect that
    # helper to the new app-wide policy so drawer/banner callers are not double-scaled.
    text, count = re.subn(
        r"return\s+PerformanceProfile\.motionDuration\(this,\s*this\.prefs,\s*j\);",
        "return HcfMotionSystem.duration(j);",
        text,
    )
    stats.motion_duration_methods += count

    text = replace_interpolators(text, stats)

    # Wrap every explicit Animator/ViewPropertyAnimator duration, including nested
    # Math.max/Math.min expressions and variable-based durations. Existing callers
    # named motionDuration* are already routed through the app's performance policy.
    text = wrap_balanced_call(
        text, "setDuration", "HcfMotionSystem.duration", "durations", stats,
        skip_contains=("motionDuration",),
    )
    text = wrap_balanced_call(
        text, "setStartDelay", "HcfMotionSystem.delay", "delays", stats,
    )

    # Infinite logo/status pulses are automatically reduced to a single pass when
    # performance/reduced-motion policy disables continuous motion.
    text = wrap_balanced_call(
        text, "setRepeatCount", "HcfMotionSystem.repeatCount", "repeats", stats,
        skip_contains=("HcfMotionSystem.repeatCount",),
    )

    if text != original:
        path.write_text(text, encoding="utf-8")
        stats.files_changed += 1


def count_central_duration_sites(text: str) -> int:
    count = text.count(".setDuration(HcfMotionSystem.duration(")
    # Both function calls such as setDuration(motionDuration(180L)) and already
    # resolved local variables such as setDuration(motionDuration2) are central.
    count += len(re.findall(
        r"\.setDuration\(\s*motionDuration(?:\w*)?\s*\(", text
    ))
    count += len(re.findall(
        r"\.setDuration\(\s*motionDuration\w*\s*\)", text
    ))
    return count


def audit(root: pathlib.Path, stats: Stats) -> None:
    java_root = root / "src"
    legacy_interpolator = re.compile(
        r"new\s+(?:android\.view\.animation\.)?"
        r"(?:AccelerateDecelerate|Decelerate|Accelerate|Overshoot|"
        r"AnticipateOvershoot|Bounce|Linear|Path)Interpolator\s*\("
    )

    remaining_legacy = []
    duration_sites = 0
    normalized_sites = 0
    for path in java_root.rglob("*.java"):
        if path.name == "HcfMotionSystem.java":
            continue
        text = path.read_text(encoding="utf-8")
        if legacy_interpolator.search(text):
            remaining_legacy.append(str(path.relative_to(root)))
        duration_sites += text.count(".setDuration(")
        normalized_sites += count_central_duration_sites(text)

    if remaining_legacy:
        raise SystemExit("Legacy interpolators remain after overhaul: " + ", ".join(remaining_legacy))
    if duration_sites and normalized_sites < duration_sites:
        raise SystemExit(
            f"Motion audit failed: only {normalized_sites}/{duration_sites} setDuration sites use central policy"
        )
    if stats.interpolators < 1 or stats.durations < 1:
        raise SystemExit("Motion overhaul did not find enough animator sites to normalize")

    print("HCF full native motion overhaul applied")
    print(f"  Java files scanned: {stats.files_scanned}")
    print(f"  Java files changed: {stats.files_changed}")
    print(f"  Duration sites normalized: {stats.durations}")
    print(f"  Start delays normalized: {stats.delays}")
    print(f"  Repeat-count sites normalized: {stats.repeats}")
    print(f"  Legacy interpolators replaced: {stats.interpolators}")
    print(f"  Existing motionDuration helpers redirected: {stats.motion_duration_methods}")
    print(f"  Audited central duration sites: {normalized_sites}/{duration_sites}")


def main() -> None:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "source code").resolve()
    java_root = root / "src"
    if not java_root.is_dir():
        raise SystemExit(f"Java source root not found: {java_root}")

    policy = java_root / "com/harleytg/forum/motion/HcfMotionSystem.java"
    if not policy.is_file():
        raise SystemExit(f"HcfMotionSystem missing: {policy}")

    stats = Stats()
    for path in sorted(java_root.rglob("*.java")):
        normalize_java(path, stats)
    audit(root, stats)


if __name__ == "__main__":
    main()
