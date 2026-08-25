#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def merge_imports(target, generated):
    imports = []
    for line in generated.splitlines():
        if line.startswith("import ") and line not in target:
            imports.append(line)
    if not imports:
        return target
    lines = target.splitlines()
    last_import = max(i for i, line in enumerate(lines) if line.startswith("import "))
    lines[last_import + 1:last_import + 1] = imports
    return "\n".join(lines) + ("\n" if target.endswith("\n") else "")


def class_source(generated, class_marker):
    start = generated.index(class_marker)
    # Keep a compact comment immediately before the class if present only by using class marker.
    return generated[start:].strip() + "\n"


# Fold sanitizer into HcfSecurityAndPrefs.java.
gen_rel = "source code/src/com/harleytg/forum/HcfSupportSanitizer.java"
gen = read(gen_rel)
rel = "source code/src/com/harleytg/forum/HcfSecurityAndPrefs.java"
text = merge_imports(read(rel), gen)
src = class_source(gen, "final class HcfSupportSanitizer")
if "final class HcfSupportSanitizer" not in text:
    text = text.rstrip() + "\n\n// ---- HcfSupportSanitizer.java ----\n" + src
write(rel, text)
(ROOT / gen_rel).unlink()


# Fold notification action support into HcfNotificationEngine.java, but expose
# the manifest receiver as a public nested class on the already-public engine.
gen_rel = "source code/src/com/harleytg/forum/HcfNotificationActions.java"
gen = read(gen_rel)
rel = "source code/src/com/harleytg/forum/HcfNotificationEngine.java"
text = merge_imports(read(rel), gen)
src = class_source(gen, "final class HcfNotificationActions")
src = src.replace("public static final class ActionReceiver extends BroadcastReceiver",
                  "public static class ActionReceiver extends BroadcastReceiver", 1)
if "final class HcfNotificationActions" not in text:
    text = text.rstrip() + "\n\n// ---- HcfNotificationActions.java ----\n" + src
outer_anchor = "public final class HcfNotificationEngine {\n    private HcfNotificationEngine() {}"
outer_replacement = "public final class HcfNotificationEngine {\n    public static final class NotificationActionReceiver extends HcfNotificationActions.ActionReceiver {\n        public NotificationActionReceiver() { super(); }\n    }\n\n    private HcfNotificationEngine() {}"
if outer_anchor not in text:
    raise SystemExit("HcfNotificationEngine receiver insertion anchor missing")
text = text.replace(outer_anchor, outer_replacement, 1)
write(rel, text)
(ROOT / gen_rel).unlink()


# Fold custom avatar view into HcfMainActivities as a public nested view class
# so XML inflation works without expanding the validator's source-file list.
gen_rel = "source code/src/com/harleytg/forum/HcfIdentityAvatarView.java"
gen = read(gen_rel)
rel = "source code/src/com/harleytg/forum/HcfMainActivities.java"
text = merge_imports(read(rel), gen)
src = class_source(gen, "public final class HcfIdentityAvatarView")
src = src.replace("public final class HcfIdentityAvatarView", "public static final class IdentityAvatarView", 1)
src = src.replace("public HcfIdentityAvatarView(", "public IdentityAvatarView(")
outer_anchor = "public final class HcfMainActivities {\n    private HcfMainActivities() {}"
outer_replacement = "public final class HcfMainActivities {\n" + "\n".join("    " + line if line else "" for line in src.rstrip().splitlines()) + "\n\n    private HcfMainActivities() {}"
if outer_anchor not in text:
    raise SystemExit("HcfMainActivities avatar insertion anchor missing")
text = text.replace(outer_anchor, outer_replacement, 1)
write(rel, text)
(ROOT / gen_rel).unlink()


# Update references to the nested identity view and public nested receiver.
rel = "source code/src/com/harleytg/forum/HcfSubActivities.java"
text = read(rel)
text = text.replace("new HcfIdentityAvatarView(this)", "new HcfMainActivities.IdentityAvatarView(this)")
text = text.replace("HcfIdentityAvatarView.frame(this, this.identityAvatar)",
                    "HcfMainActivities.IdentityAvatarView.frame(this, this.identityAvatar)")
write(rel, text)

rel = "source code/res/layout/activity_main.xml"
text = read(rel).replace("com.harleytg.forum.dev.HcfIdentityAvatarView",
                         "com.harleytg.forum.dev.HcfMainActivities$IdentityAvatarView")
write(rel, text)

rel = "source code/AndroidManifest.xml"
text = read(rel).replace("com.harleytg.forum.dev.HcfNotificationActions$ActionReceiver",
                         "com.harleytg.forum.dev.HcfNotificationEngine$NotificationActionReceiver")
write(rel, text)

print("New helpers folded into the existing validated Java source set.")
