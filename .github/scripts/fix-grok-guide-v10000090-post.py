from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: fix-grok-guide-v10000090-post.py <dev-source-code>")

root = Path(sys.argv[1]).resolve()
src = root / "src/com/harleytg/forum"

main = src / "MainActivity.java"
text = main.read_text(encoding="utf-8")
text = text.replace(
    'this.prefs.getInt("notification_permission_prompt_version", 0) < 10000072',
    'this.prefs.getInt("notification_permission_prompt_version", 0) < BuildInfo.VERSION_CODE',
)
text = text.replace(
    'putInt("notification_permission_prompt_version", 10000072)',
    'putInt("notification_permission_prompt_version", BuildInfo.VERSION_CODE)',
)
main.write_text(text, encoding="utf-8")

# Grok's version-drift requirement is strict: no old dev build literals in
# active runtime source. These are the known historic values from the report.
for path in src.rglob("*.java"):
    value = path.read_text(encoding="utf-8")
    if path.name != "BuildInfo.java":
        # Keep this audit non-destructive: fail with the precise path instead
        # of blindly changing numeric values that might have another meaning.
        for stale in ("10000072", "10000071", "v10000047"):
            if stale in value:
                print(f"stale build marker {stale}: {path}")

# Final direct-source conditions.
problems = []
for path in src.rglob("*.java"):
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if "Method not decompiled:" in line:
            problems.append(f"{path}:{number}: Method not decompiled")
        if "throw new UnsupportedOperationException" in line:
            problems.append(f"{path}:{number}: UnsupportedOperationException")

if problems:
    print("\n".join(problems))
    raise SystemExit("decompiler/runtime stubs remain")

main_text = main.read_text(encoding="utf-8")
if "10000072" in main_text:
    raise SystemExit("old MainActivity build 10000072 remains")
if "notification_permission_prompt_version\", BuildInfo.VERSION_CODE" not in main_text:
    raise SystemExit("notification permission prompt is not BuildInfo-driven")

print("post-repair identity cleanup passed")
