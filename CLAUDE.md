# CLAUDE.md

**LOCAL FILE — NOT VERSIONED.**
Listed in `.gitignore`. Must never be committed or tracked by Git, and must
never be referenced from any tracked file.
`CLAUDE.md` is intentionally included in project ZIP archives supplied to
Claude Code so that local Claude instructions survive across sessions.
If this file ever shows up in `git status` as tracked or staged, that is a
defect to fix immediately.

Working notes for Claude Code on PaperScrape. Permanent project rules live in
`AI_PROJECT_RULES.md` (tracked) — this file holds environment setup, commands
and session-local reminders.

---

## 1. Read these first, every session

In this order, before any significant task:

1. **`ROADMAP.md`** — **the authoritative operational plan.** Read this first:
   it states the current phase, what is already done, and the next approved
   task. Never decide what to work on from conversation memory.
2. `AI_PROJECT_RULES.md` — the permanent rules
3. `ARCHITECTURE.md` — how it actually works today
4. `DESIGN_NOTES.md` — the visual design system
5. `RELEASE_HISTORY.md` — what shipped, what is known broken
6. `README.md` — what the app is (note: currently out of sync, see §7)

`ROADMAP_OLD.md` was deleted at the v1.0 cleanup. `ROADMAP.md` is the plan, and
it is short on purpose: it holds only work that is still worth doing. Anything
not in it is not scheduled.

When a phase is completed, blocked, reordered or materially changed, update
`ROADMAP.md` **before** treating the release as complete.

---

## 2. The three rules that are easiest to break

1. **Never claim a tool or capability without verifying it in the current
   session.** The environment resets. The Android SDK you installed last time
   is gone.
2. **This file stays local and untracked.**
3. **Never publish. Not once, not "because the batch said so".** See below.

### Publishing is the maintainer's, never yours

The policy is `AI_PROJECT_RULES.md` §10.A and §11.D. Operationally, on this
machine:

**Never run any of these:**

```
git push … (any form, any remote, any URL, tags included)
gh release create / edit / upload
gh api … -X POST/PATCH/PUT/DELETE   against this repo
git remote set-url …                to make a push work
```

**Never use the maintainer's credentials to reach GitHub.** `~/.ssh/id_rsa` on
this machine authenticates as `urgali` and *will* let a push through. That is
exactly why it must not be touched. Nor `git@github.com:...` URLs, nor a token,
nor `gh auth`, nor a credential helper.

**`origin` is the HTTPS URL and there are no credentials for it, so
`git push origin main` fails with `could not read Username for
'https://github.com'`. That failure is the system working.** Do not route
around it. Stop, finish the ZIP, hand it over.

Read-only and local Git are fine and useful: `status`, `log`, `diff`, `show`,
`tag --list`, and a local commit as a checkpoint. Reading the public GitHub API
with unauthenticated `curl` (release list, tag SHAs) is fine too — it writes
nothing.

**What you deliver instead** is `PaperScrape_vX_Y.zip`, verified per §5.6. A
batch ends with a delivered archive and a report saying publication is
outstanding. Never write "released", "shipped" or "published".

### CLAUDE.md and project ZIP archives

The local CLAUDE.md file is intentionally included in project ZIP archives
supplied to Claude Code, even though it must remain ignored and untracked by
Git.

When preparing a project ZIP for a new session, include CLAUDE.md so the local
Claude Code instructions are preserved across sessions.

Never commit CLAUDE.md to Git.

### The external reference is gone (v3.0)

There used to be a third rule here, and a name stored in this file: an external
product PaperScrape was compared against, whose name was forbidden inside the
repository and which had to be scanned for before every release.

**That is over.** v3.0 removed every operational dependency on it -- the source
comments that cited it as the authority for a design decision, the rules that
required consulting it, and the release-gate scan. The name is no longer stored
here, because there is nothing left for it to be checked against, and
`AI_PROJECT_RULES.md` §2 now says plainly that PaperScrape is standalone.

Two things follow for future sessions:

- **Do not go looking for it.** If a question cannot be answered from this
  repository, from Android's documentation, or from watching the app run, ask
  the maintainer. Do not resolve it against somebody else's product.
- **`CHANGELOG.md` and the pre-v2.0 release notes still mention it obliquely,
  and that is deliberate** -- see `AI_PROJECT_RULES.md` §3. They are history.
  Do not "clean" them.

---

## 3. Environment setup (ephemeral sandbox)

The filesystem resets between sessions. This whole section must be re-run each
time a build is needed. Verified working; takes roughly 5 minutes plus build
time.

### 3.1 What the base image already has

| Tool | Status |
|---|---|
| Ubuntu 24.04, root, network open | ✅ |
| JRE 21 | ✅ but **no `javac`** — not usable for Gradle |
| Python 3.12, Pillow, numpy | ✅ |
| ImageMagick (`convert`, `identify`) | ✅ |
| Gradle, Android SDK | ❌ absent |
| SVG rasteriser (cairosvg, rsvg, inkscape) | ❌ absent (`pip install --break-system-packages cairosvg` if needed) |
| Emulator / device | ❌ absent and impractical: 1 CPU, no KVM |

Resources: **1 CPU, ~4 GB RAM, ~10 GB free disk.** This matters — see §3.5.

### 3.2 JDK 17 (matches CI)

```bash
mkdir -p /home/claude/tools && cd /home/claude/tools
curl -sSL -o jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk17.tar.gz && rm jdk17.tar.gz
export JAVA_HOME=/home/claude/tools/jdk-17.0.20+8   # adjust to the extracted dir
export PATH=$JAVA_HOME/bin:$PATH
javac -version    # must print a version — the system JRE 21 has no javac
```

### 3.3 CA trust fix — **required, non-obvious**

Egress goes through a TLS-intercepting proxy using a private CA. `curl` trusts
it via the system store; **the JDK does not**, so `sdkmanager` and Gradle fail
to download while `curl` to the same URL succeeds. Symptom: `sdkmanager --list`
returns nothing and hangs on manifest fetch.

```bash
KS=$JAVA_HOME/lib/security/cacerts
for c in /usr/local/share/ca-certificates/*.crt; do
  keytool -importcert -noprompt -trustcacerts -keystore "$KS" \
    -storepass changeit -alias "$(basename "$c" .crt)" -file "$c"
done
```

Do this **before** trying the SDK install, not after debugging it again.

### 3.4 Android SDK

```bash
export ANDROID_HOME=/home/claude/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
mkdir -p $ANDROID_HOME/cmdline-tools
curl -sSL -o /tmp/clt.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
unzip -q /tmp/clt.zip -d /tmp/clt
mv /tmp/clt/cmdline-tools $ANDROID_HOME/cmdline-tools/latest

yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
  "platform-tools" "platforms;android-37" "build-tools;36.0.0"

echo "sdk.dir=$ANDROID_HOME" > local.properties   # gitignored
```

`yes |` is needed on **both** commands — the install re-prompts for licences
and silently skips every package if it is not piped.

### 3.5 Build (Level 3 only — see §5.1)

`assembleDebug` is an escalation step, not a routine one. At Level 2 the
compile is already proven by `test`; what this adds is packaging, resource
linking and dexing.

```bash
cd <project root>
chmod +x gradlew
./gradlew --no-daemon --no-parallel \
  -Dorg.gradle.jvmargs="-Xmx1400m -Dfile.encoding=UTF-8" \
  assembleDebug
```

**Memory override is required.** `gradle.properties` requests `-Xmx2048m`,
which on a 4 GB single-CPU box gets the build killed with no error message at
all — the log simply stops and no Java process remains. `1400m` works.

**Background jobs need `setsid`.** A plain `nohup … &` is killed when the shell
call returns. Use:

```bash
setsid nohup ./gradlew … > /tmp/build.log 2>&1 < /dev/null &
```

Measured timings on this hardware (cold, including dependency download):

| Task | Time |
|---|---|
| `assembleDebug` | ~11 min 40 s |
| `lintDebug` (after a build) | ~4 min 15 s |

Budget for this. Do not start a build near the end of a session.

### 3.6 Running the tests

```bash
./gradlew --no-daemon --no-parallel \
  -Dorg.gradle.jvmargs="-Xmx1400m -Dfile.encoding=UTF-8" testDebugUnitTest
```

~3 min with a warm Gradle cache. `./gradlew test` (what CI runs) resolves to
the same work. Results:

```bash
python3 -c "
import xml.etree.ElementTree as ET, glob
t=f=e=0
for x in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(x).getroot()
    t+=int(r.get('tests')); f+=int(r.get('failures')); e+=int(r.get('errors'))
print(t,'tests,',f,'failures,',e,'errors')"
```

Baseline as of Phase 0: 50 tests. As of v2.16: **688 tests, 0 failures**, plus
12 Python tests for the asset tooling
(`cd tools/assets && python3 -m unittest discover -s tests`).

Gotcha: `org.json` is a framework class, so unit tests need the real
implementation on the test classpath (`testImplementation("org.json:json:…")`).
Without it every `JSONObject` call throws "not mocked". Do not "fix" that by
enabling `isReturnDefaultValues` — that silently turns real assertions into
assertions about stub defaults.

### 3.7 Useful checks

```bash
# Sprite inventory: size, bbox, transparent padding
python3 - <<'EOF'
from PIL import Image; import glob, os
for p in sorted(glob.glob("app/src/main/res/drawable-nodpi/*.png")):
    im = Image.open(p); w, h = im.size
    bb = im.convert("RGBA").getchannel("A").getbbox()
    print(f"{os.path.basename(p):40} {w}x{h}  bbox={bb}")
EOF

# Byte-identical duplicate assets
find app/src/main/res -name '*.png' -exec sha256sum {} + | sort | uniq -w64 -D

# Orphan drawables (present but never referenced)
comm -13 <(grep -rhoP 'R\.drawable\.\K[a-z0-9_]+' app/src --include='*.kt' | sort -u) \
         <(ls app/src/main/res/drawable-nodpi/*.png | xargs -n1 basename | sed 's/\.png$//' | sort)

# Lint results as structured data
python3 -c "import xml.etree.ElementTree as ET,collections;print(collections.Counter(i.get('id') for i in ET.parse('app/build/reports/lint-results-debug.xml').getroot().findall('issue')))"
```

---

## 4. Project quick facts

- Single Gradle module, `app`. Package `com.paperscrape.livewallpaper`.
- Kotlin. The scene is **2D throughout**, but there are **two backends** behind it and
  neither the scene code nor you should assume which one is live. `PaperRenderer`
  composes the scene and `SceneObjectRenderer` draws the objects; both draw onto the
  `SceneCanvas` interface, implemented by `GlSceneTarget` (**OpenGL ES 2.0, on a
  per-engine render thread — the normal path**) and by `CanvasSceneTarget` (2D
  `Canvas` — the fallback when EGL is unavailable, and what the settings preview
  uses). `ARCHITECTURE.md` §3 is the authority.
  **This entry used to read "2D `Canvas` rendering. No OpenGL." That is false** and
  had been false since the GL backend landed; do not carry the old claim forward.
  The *visual style* is 2D paper-cutout (that part never changed), and the settings
  UI is Compose Material 3.
- `minSdk 26`, `compileSdk 37`, `targetSdk 37`, AGP 9.3.1, Gradle 9.7.1, Java 17.
  **Both SDK levels are 37.** They were one apart until v4.0, whose whole point
  was raising `targetSdk` from 36 to 37 after re-assessing every Android 17
  behaviour change against this app's code. They are still two settings doing two
  different jobs — `compileSdk` is only what the code is compiled and linked
  against (37 is required by `core 1.19` / Compose `1.12`), while `targetSdk` is
  what the platform's behaviour gates read — and they simply hold the same value
  now. Do not collapse one into the other, and do not assume from one what the
  other is: read them from `app/build.gradle.kts`.
- Kotlin comes from AGP's built-in support — the `org.jetbrains.kotlin.android`
  plugin is intentionally **not** applied. Do not add it. The Kotlin *version* is
  whatever `org.jetbrains.kotlin.plugin.compose` is set to in the root
  `build.gradle.kts` (currently 2.2.21): AGP resolves `kotlinCompilerClasspath`
  to that version, so bumping the Compose plugin bumps the compiler with it.
- Sprites live in `res/drawable-nodpi/` — `nodpi` is deliberate, do not move
  them to a density bucket.
- `versionCode` in `app/build.gradle.kts` drives the release tag.
- `debug.keystore` is committed on purpose.
- Two sprite scale conventions exist. Every sprite is blitted through
  `SpriteBlitter`, and the convention is a `SpriteScale` argument:
  `SCENE_UNITS` (authored 3× and scaled down) or `CANVAS_PIXELS` (authored at
  on-screen size). Passing the wrong one is a silent 3× error. Check what the
  sprite was authored at before adding a call — nothing in the PNG says.
  `SceneObjectRenderer` is single-convention and binds `SCENE_UNITS` in two thin
  wrappers; `PaperRenderer` mixes both and names the scale at every call site.

---

## 5. Releases: level, identifier, checklist

The policy is `AI_PROJECT_RULES.md` §11 and §12. This is the operational side of
it — what to actually type.

**"Release" here means *prepare* a release.** Bumping the version, writing the
notes, verifying, and building the ZIP are all yours. The tag, the push and the
GitHub Release are the maintainer's and are never yours (§2, `AI_PROJECT_RULES.md`
§10.A / §11.D).

### 5.1 Pick the verification level first

| Level | When | Run |
|---|---|---|
| **1** | Only non-executable docs changed | no Gradle at all |
| **2** *(default)* | Kotlin/Java, tests, resources, assets, app behaviour | `test`, `lintDebug`, relevant static checks |
| **3** | Gradle/build config, manifest, CI, lifecycle, critical rendering, asset pipeline, big architectural change, release candidate, or the maintainer asks | Level 2 **plus** `assembleDebug` **plus** clean-extraction rebuild |

**`assembleDebug` is not a default step.** Skipping it at Level 1 or 2 is correct
behaviour, not an omission. When skipped, write verbatim in the report:

`assembleDebug intentionally skipped under normal verification policy.`

When unsure which level applies, escalate.

### 5.2 Find the next release identifier — never from memory

The project may be continued from another session or another Claude account, so
conversation memory is not evidence.

```bash
git tag --list 'v*' | sort -V | tail -10
# read-only, unauthenticated: writes nothing, needs no credential
curl -s https://api.github.com/repos/urgali/paperscrape/releases \
  | grep -o '"tag_name": *"[^"]*"' | head
grep -n "versionCode = \|versionName = " app/build.gradle.kts
```

| You want | Tag | versionName | versionCode | GitHub |
|---|---|---|---|---|
| A shipped stable release | `v1.1` | `1.1` | previous + 1 | Stable, marked latest |

There is currently no pre-release tag form. A beta syntax will be added when it
is needed; until then a tag is a stable release or it is rejected.

Rules CI enforces, so getting them wrong fails the release rather than
publishing something wrong:

- The tag must be `vMAJOR.MINOR` and must equal `versionName`. `v1.1` requires
  `versionName = "1.1"`.
- Anything that is not `vMAJOR.MINOR` is rejected.
- An existing tag or release is never overwritten.

`versionCode` is **not** checked against the tag. It is Android's install
counter: increment it by one for every release and leave it alone otherwise
(§11.A).

Write `release-notes/<tag>.md` matching the tag exactly — `release-notes/v1.1.md`
for `v1.1`.

**Releases are cut by pushing a tag, not by merging.** Merging to `main` only
builds and tests.

### 5.3 Checklist — every level

The forbidden-name scan that used to open this checklist was retired in v3.0
(see §2 and `AI_PROJECT_RULES.md` §2). Nothing replaces it: there is no external
reference left to scan for.

```bash
# Hidden files intact
find . -name ".*" -not -name "." ; ls .github/workflows/

# CLAUDE.md really ignored
git check-ignore -v CLAUDE.md            # must print the matching rule
git add -A && git ls-files --error-unmatch CLAUDE.md   # must FAIL

# ZIP completeness: every file on disk must be in the ZIP
diff <(find . -type f | sed 's|^\./||' | sort) \
     <(unzip -Z1 "$ZIP" | grep -v '/$' | sed 's|^PaperScrape/||' | sort)
```

### 5.4 Checklist — Level 2 and above

```bash
./gradlew --no-daemon --no-parallel \
  -Dorg.gradle.jvmargs="-Xmx1400m -Dfile.encoding=UTF-8" test lintDebug
```

Plus any static check the change earns — an allocation audit with `javap` when a
draw path was touched, a compatibility fixture when persisted data changed.

Mutation testing only where §12.10 says it applies. If a mutation does not break
a test, that is a finding about the test: replace the mutation, do not report a
pass.

### 5.5 Close with the release report

Fill in the template in §12.14 verbatim, and split Claude-side from
maintainer-side verification. The maintainer normally does: local APK build,
install on device, visual check, and practical CPU/battery observation. **Never
present any of those as done.**

Then update `RELEASE_HISTORY.md`, and `ROADMAP.md` / `ARCHITECTURE.md` /
`DESIGN_NOTES.md` / `AI_PROJECT_RULES.md` if the release changed what they
describe.

**The report ends with the ZIP, not with a tag.** State: version prepared, notes
written, archive delivered, publication outstanding and the maintainer's.

### 5.6 Build and verify the delivery ZIP

Policy: `AI_PROJECT_RULES.md` §12.F. Every modifying batch delivers one.

```bash
cd "$(dirname "$PROJECT")"
ZIP=/path/to/PaperScrape_v3_2.zip
rm -f "$ZIP"
zip -r -q "$ZIP" PaperScrape \
  -x 'PaperScrape/.git/*' 'PaperScrape/build/*' 'PaperScrape/app/build/*' \
     'PaperScrape/.gradle/*' 'PaperScrape/.kotlin/*' 'PaperScrape/.idea/*' \
     'PaperScrape/local.properties'
```

`zip -r` on the directory picks dotfiles up automatically — `.gitignore`,
`.github/`, `CLAUDE.md` are all included without naming them. Verify it rather
than trusting it:

```bash
# present
unzip -Z1 "$ZIP" | grep -E 'CLAUDE\.md|\.gitignore|AI_PROJECT_RULES\.md|\.github/workflows/'
# absent
unzip -Z1 "$ZIP" | grep -E '\.git/|/build/|\.gradle/|local\.properties|\.apk$|\.jks$|\.keystore$' \
  | grep -v 'debug\.keystore'      # debug.keystore is the one deliberate exception
# completeness against the working tree
diff <(cd "$PROJECT" && git ls-files) \
     <(unzip -Z1 "$ZIP" | sed 's|^PaperScrape/||' | grep -v '/$' | sort)
```

Then extract to a clean directory and build **from there**, not from the working
tree:

```bash
rm -rf /tmp/zipcheck && mkdir -p /tmp/zipcheck && unzip -q "$ZIP" -d /tmp/zipcheck
cd /tmp/zipcheck/PaperScrape
ANDROID_HOME=$HOME/Android/Sdk ./gradlew test lintDebug assembleDebug
```

A build that only ever succeeded in the working tree proves nothing about the
archive: a file that is gitignored but load-bearing, or one that was never
added, fails exactly here and nowhere else.

## 6. Session hygiene

- Delete `.gradle/` and `.kotlin/` from the project root before handing the
  tree back. They are gitignored, but they bloat any archive.
- `local.properties` is gitignored and contains a machine-specific path. It
  will be wrong in the next session; just rewrite it (§3.4).
- Never delete anything starting with `.` without checking what it is.

---

## 7. Live gotchas

> ### THE TEST DEVICE CHANGED ON 2026-09-05 — read this before quoting any number below
>
> The **OnePlus 6T is gone and is not coming back.** The device every measurement in this file was
> taken on no longer exists for this project, so **no A/B against it is possible any more**: where
> something differs, say it is not attributable rather than picking an explanation.
>
> The current device is a **Blackview BV6600**: MediaTek Helio A25 (`mt6765`), **eight Cortex-A53**
> (4×1.801 GHz + 4×1.500 GHz, no big cores), **PowerVR GE8320**, **720×1440 at density 320**,
> **Android 10 (SDK 29)**. `minSdk = 26`, so the app installs. Its launcher is
> `com.blackview.launcher/…SearchLauncher`, it has **one home page**, and it **never calls
> `onOffsetsChanged`** — verified with a local probe, positive control being the wallpaper picker,
> which calls it once with `xOffset=0.0`. Horizontal motion in the scene is `scrollProgress`'s own
> accumulator: 0.73–0.80 px/s on the buildings, 0.20 px/s on the far hills.
>
> **The numbers that replace the ones further down** (simil-release build, Autumn, 60 s windows,
> n=3, protocol unchanged): process **43.56%** of one core (sd 0.50), `PaperScrapeGlTh` **42.71%**,
> hidden **0.137%**, frame rate **29.60 fps** from SurfaceFlinger, and therefore **14.72 ms of CPU
> per frame against a 33.3 ms interval — 44% of the budget, so the margin holds.**
>
> **GPU busy is not measurable here and must not be estimated:** `kgsl` is Adreno-only and absent;
> MediaTek's `ged` nodes are root-only (*Permission denied*); the only graphics tracepoint is
> `pvr_fence`, which counts fences, not occupancy.
>
> **The Canvas goldens are bit-deterministic here** — 24 scenes byte-identical across four
> separate runs including a process restart and a device reboot.
>
> **The 24 Canvas goldens were re-authored on this device on 2026-09-05** (`BACKLOG_v4_22.md` item
> 36), after every one of the 24 differences was classified as renderer-shaped: sub-tolerance sky
> dithering over 22–42% of the frame, plus 0.007–0.099% over tolerance scattered in components
> **never larger than 12 pixels**. Before that, the OnePlus-authored goldens left a constant
> 0.1223% offset inside the people-density gate's rectangle — 86% of its margin — which is not
> noise but an artefact of comparing against hardware that no longer exists. **Every frame and
> every asserted focus now measures zero differing pixels**, so all four gates sit on a real zero
> floor. The gates themselves were not moved and `SettingsGates` was not touched.
>
> **The three GL goldens pass on PowerVR** — edge displacement 0.00% / 0.01% / 0.24% against a 3%
> gate — which closes `BACKLOG_v4_21.md` item 20 with an observation.

- **The in-app updater hangs on `Downloading` (D13).** Reported against v2.15, still open after
  v2.16, and **not investigated** — do not repeat any theory about the cause, because none has
  been established. Closing it means reproducing the hang on an emulator against a *real* GitHub
  release, then running check → download → SHA-256 verify → install end to end on that release.
  v2.15 → v2.16 is the first pair of published releases that makes such a run possible.
- **README is out of sync.** It lists Live Weather as missing (it exists) and
  omits clouds, precipitation, rainbow, mountains, lake, birds, moon phases,
  vehicle types and people. Do not trust it as a feature inventory; trust
  `ARCHITECTURE.md`.
- **v66–v72 shipped unverified** — no Android build tools were available then.
  Treat that range as less proven.
- **`elapsedSeconds` freezes at ~12 days** of visible uptime. If a bug report
  says "the wallpaper stopped moving", this is the first suspect.
- **The asset generators are lost, but a replacement pipeline now exists.**
  `tools/assets/` (Phase 3.1) regenerates from committed SVG sources every shipped sprite that
  has one — **134 of 266 as measured at v4.21** — while the other **132** are the per-skin-tone
  recolours, which carry `source.kind = "none"` and name the generator that makes them
  (`tools/generate_skin_variants.py`) in `tools/assets/sources/sprites.json`. Re-measure rather
  than quoting these: `paperscrape-assets validate` prints both numbers,
  which since Phase 3.5 also declares every seasonal variant group and whether its
  members currently differ.
  Set up with `pip install --break-system-packages -r tools/assets/requirements.txt`
  and always run `python3 -m paperscrape_assets probe` first — a fingerprint
  mismatch invalidates every recorded fidelity figure. The tooling is **not** part
  of the Gradle build, and `render` refuses to write into `res/drawable-nodpi/`.
- **A green test suite on first run is a warning sign, not a result.** Break the
  code under test, confirm the test fails, revert. `SceneTheme.equals` compares
  by `id` alone, so a whole-object equality assertion passes even when every
  colour has been lost in a round trip.
- **A window is cool by day and warm at night, and `windowGlassColor` is the only thing that says
  so.** `WINDOW_GLASS_DAY`/`WINDOW_GLASS_NIGHT` in `SceneObjectRenderer` are the two ends; the
  restaurant and the tower both crossfade between them on `nightGlow`. A tintable window asset is a
  **white mask** in this set (`restaurant_window`, and since v4.12 `skyscraper_wall_lit`) -- an
  asset that carries its own colour can only ever be that colour. `SkyscraperWindowTest` pins the
  coupling by reading the call sites, because two mutations of exactly this rule slipped past the
  whole JVM suite when only the instrumented goldens covered it.
- **There is one authority for automatic day/night colours, and it is `DayNightColor`.** Since
  v4.13 the transform works in **CIELAB**: hue held, `L*` x0.28, chroma x0.72 (x0.50/x0.80 until
  v4.14 — one sample is not a matrix), plus a small push
  towards blue scaled by lightness so black stays black. Out-of-gamut results give up chroma (or,
  in the inverse direction, walk back towards the colour the user picked) rather than clipping
  channels, because clipping drags the hue.
  **The v4.12 factors were fitted to the 41 authored day/night pairs and that was the wrong thing
  to fit**: those pairs encode per-object artistic intent, not a lighting law -- the sky goes to
  0.12 of its lightness, snow stays at 0.87 -- so the median produced a compromise that left white
  at a mid grey. The constants now come from the requirement and were settled on a device. It is
  applied in exactly one place:
  `CustomThemeRegistry.resolveActiveCustomization`, which the renderer, the settings screen and
  the theme gallery all already resolve through. Do not derive a colour anywhere else, and in
  particular do not derive one per frame. **Nothing ever writes a derived colour back to the
  DataStore** -- the stored pair always holds the user's own two values, which is the entire
  reason switching a pair back to Manual restores them.
- **Measuring wallpaper CPU on a debug build tells you almost nothing.** On a OnePlus 6T the
  debug build sat at ~120% CPU (of 800%) with the wallpaper visible; the release build of the same
  version sat at **~28%**. `top -H` explains it: ~48% was the JIT thread pool and the rest a slower
  interpreted render path. Measure the release build, or say plainly that the number is a debug
  one. Hidden, both drop to **0.0%** -- the engine idles correctly and that is the figure that
  matters for battery.

  **Re-measured properly on 2026-09-05 (v4.22 debug, Autumn, OnePlus 6T), and the numbers to quote
  are these** -- `BACKLOG_v4_22.md` item 32 and `V4_22_MISURA_CPU_REPORT.md` carry the protocol:

  | | % of **one** core |
  |---|---|
  | whole process, visible (n=18, sd 1.11) | **108.45%** |
  | `PaperScrapeGlTh`, the draw loop | **67.50%** |
  | `Jit thread pool` | 37.22% |
  | whole process, **hidden** (n=3) | **0.12%** |

  Two things follow. **The "67-68%" that `BACKLOG_v4_21.md` item 27 carries is the render thread,
  not the process** -- it reproduces exactly, but as a process figure it was never right.
  And **v4.21 measures 109.00% under the same protocol**, i.e. indistinguishable from v4.22
  (-0.55 pt, 0.49 sd), so v4.22's two new per-frame evaluations cost nothing measurable.

  **A release-*like* build IS buildable here, and the v4.22 GL waste audit (2026-09-05) built one.**
  The maintainer's release signing genuinely is unavailable -- `assembleRelease` takes it only from
  env vars the project deliberately does not provide, and an unsigned APK will not install, so a
  *real* release is still not buildable and a release key must never be generated. But the thing
  that actually changes the generated code is `isMinifyEnabled` + `isDebuggable = false`, not whose
  key signs it. A local build type that does `initWith(release)` and signs with the **committed**
  `debug.keystore`, carrying the debug build's `.debug` applicationId, installs over the debug build
  and cannot collide with the maintainer's own installed release. Keep it local: never commit it and
  never ship it in a ZIP.

  **Measured that way, same scene, same conditions, no instrumentation** (`BACKLOG_v4_22.md` item 33
  and `V4_22_AUDIT_SPRECO_GL_REPORT.md`):

  | | debug | release-like | ratio |
  |---|---|---|---|
  | whole process | **102.69%** of one core | **27.72%** | 3.70x |
  | `PaperScrapeGlTh` | **64.06%** | **24.42%** | 2.62x |
  | `Jit thread pool` | 33.61% | **absent** | -- |

  So the "67-68%" carries **two** qualifiers, not one: it is the **render thread** (item 32) **of a
  debug build** (item 33). On the user's phone that thread is at **24.42% of one core out of eight**.
  The old ~120% vs ~28% observation in this bullet is corroborated, not replaced.

  **Reading `/proc/<pid>/stat` still works with `debuggable = false`**, so the protocol above carries
  over unchanged. `run-as` does not -- if you need the app's DataStore, do it while a debuggable
  build is installed.

  **Trap when splitting per thread:** `/proc/<pid>/task/<tid>/stat` puts `comm` in parentheses and
  **`Jit thread pool` contains spaces**, so `awk '{print $14, $15}'` silently reads the wrong two
  columns and reports the JIT thread as 0. Cut through the comm first --
  `sed 's/.*) //' stat | awk '{print $12+$13}'` -- and check the per-thread sum reconciles with
  `/proc/<pid>/stat` (it does, to within 0.08 pt).
- **The GL goldens are tied to the driver they were captured on, and since v4.21 that driver is
  this phone's.** They live in `androidTest/assets/golden/gl-*.png`. They were taken under the
  emulator's `swiftshader_indirect` until v4.21 re-authored them on the OnePlus 6T's Adreno 630,
  because the tree redraw moved all three by 8.80% against a 3% gate and this machine has no
  emulator to regenerate them on. The gap between the two drivers is symmetric and characterised
  -- 0.92-1.18% of the outline, against a gate of 3% -- so flipping which side is exact left the
  gate untouched; `BACKLOG_v4_21.md` item 19 carries the decision and item 20 the one thing it
  does not prove.

  What has not changed is the rule: the Canvas goldens are software-rendered and portable, the GL
  ones are a regression check against **one** reference driver, and you do not raise a tolerance to
  make a different driver pass -- that trades a real check for a green tick. What has changed is
  which environment is the reference. Regenerate them on the device; the first run on a machine
  with an emulator has to confirm all three still pass there.

  **The 24 Canvas goldens are now BV6600-authored (2026-09-05); the three GL ones are still the
  Adreno-authored files and were deliberately not touched.** The two sets no longer come from the
  same device, and that is fine — the Canvas path is software rendering and the GL goldens are a
  driver check — but it is worth knowing before reading either.

  **v4.23 re-authored 16 of the 24 here (2026-09-06) and again left the three GL ones alone.** The
  sky redraw moved 16 Canvas frames; the other 8 were byte-identical, because the sun sits behind
  the cloud band in those scenes. The GL goldens were left because the *Canvas* frames of all three
  GL scenes (`day`, `lake-busy`, `thunderstorm`) came out byte-identical — nothing in those scenes
  changed — and `GlDriverGapGuardTest` still reads day 0.00% / lake-busy 0.01% / thunderstorm 0.24%
  against the 3% gate, the same three numbers as v4.22. **A byte comparison is the wrong metric for
  the GL goldens**: freshly captured GL frames differ from the committed ones by 0.49–0.94% at
  tolerance 8 simply because those files were authored on an Adreno, and that is the characterised
  driver gap, not a change.

  **Confirmed on a second driver, 2026-09-05.** The "first run on another environment" happened,
  and it was not an emulator: the test device became a **PowerVR GE8320**. All three still pass,
  by a wide margin -- `GlDriverGapGuardTest` reads **day 0.00%, lake-busy 0.01%, thunderstorm
  0.24%** against the 3% gate, i.e. *closer* than the 0.92-1.18% Adreno-versus-emulator gap. The
  GL goldens survive a change of GPU vendor. `BACKLOG_v4_21.md` item 20 is closed.

- **Do not count goldens by listing the directory.** `androidTest/assets/golden/` holds the Canvas
  PNGs *and* the three `gl-*.png`. The Canvas count is the number of Canvas **assertions** --
  `SceneGoldenTest`, `PeopleGoldenTest` and (since v4.22) `SettingsGateScenesTest`, one
  `assertMatches` each, no parameterised tests. A
  v4.19 report labelled the directory count "Canvas goldens" and a later document added the three
  GL ones on top of it, which is how "30 Canvas goldens" entered the handover notes for a suite
  that had 24. v4.21 corrected the documents and removed two duplicate frames; **counted on
  2026-09-06, after v4.23 round 2C added `halloween-moon`**, it is
  **26 Canvas assertions, 3 GL, 28 committed PNGs** (Fase 5 of v4.22 added three gate scenes:
  `traffic-day-sparse`, `traffic-night-quiet` and `shops-closed-night`, in
  `SettingsGateScenesTest`; round 2C added `SceneGoldenTest.halloweenMoon`, the first committed
  frame in which a celestial body is drawn clear of the cloud band -- see `BACKLOG_v4_23.md` items
  40 and 41). `GoldenUniquenessTest` is the guard that
  keeps two names from sharing one picture. Note the asymmetry it allows and the reason: two *tests*
  may assert one PNG — `SceneGoldenTest.day` and `PeopleGoldenTest.people-at-full-density` both
  measure `day.png`, with different focus rectangles — because a focus is an assertion over a region
  of the same frame. What must not repeat is two *PNGs* of one scene.
- **A committed golden is not a golden until the androidTest APK is rebuilt.** The expected PNGs
  are *assets of the instrumented APK*, not files the test reads off the host, so copying one into
  `app/src/androidTest/assets/golden/` and then running `am instrument` against an APK built before
  the copy compares against an APK that does not contain it. The failure says
  `No golden committed for '<name>'` and writes the frame out again, which reads like the copy
  never happened. **Cost this the whole 43-minute suite once, on 2026-09-06.** Run
  `installDebugAndroidTest` after committing a PNG, and check it went in:
  `python3 -c "import zipfile;print([n for n in zipfile.ZipFile('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').namelist() if 'golden' in n])"`.

- **Capturing goldens on this device: use `am instrument`, and `getExternalFilesDir` works.**
  `-e updateGoldens true` writes the rendered frame without ever comparing it. Drive it with
  `adb shell am instrument -w -r -e updateGoldens true -e class <classes> \
  com.paperscrape.livewallpaper.debug.test/androidx.test.runner.AndroidJUnitRunner` — **not** Gradle
  with a class filter, which uninstalls the package at the end and takes the written files with it.
  `SceneGolden` writes to `getExternalFilesDir(null)/golden-output` and also emits each frame to
  logcat as base64 under the `GOLDENPNG` tag; `GlGolden` writes to the same directory but does
  **not** emit. The v4.22 migration report says `getExternalFilesDir` is unavailable to the app here
  and that `outputDir()` had to be pointed at `filesDir`: **that did not reproduce on 2026-09-06** —
  the GL harness, untouched, wrote all three frames to
  `/sdcard/Android/data/com.paperscrape.livewallpaper.debug/files/golden-output/`. Either way,
  `run-as com.paperscrape.livewallpaper.debug cat files/…` reads the private directory on a debug
  build, so both paths are retrievable. The whole 148-test instrumented suite takes roughly
  40 minutes on this phone; `theGatesStandBetweenFloorAndSignal` alone takes ~4.

- **R8 gets OOM-killed here with a busy machine.** The release-like build type's
  `minifyRellikeWithR8` died with no error message at `-Xmx2400m` while the instrumented suite was
  running — the same silent death `3.5` describes. Run it with nothing else going on.

- **Do not fix a size/alignment bug with a per-asset constant.** That is how
  the project accumulated five such patches in three releases. Find the
  system-level cause.
- Emoji are currently used as settings section markers. That is a de facto
  convention, not an approved design decision.
- **Never add a raw `Slider` to the settings screen.** Use `PreferenceSlider`,
  which commits once on drag end. A raw `Slider` bound to a preference puts a
  disk round trip inside the thumb's own feedback loop. Put the value caption in
  its `label` lambda so it stays live during the drag.
- **A scripted edit is not finished until it has been read line by line.** The
  regex conversion of the 16 slider sites left `it` instead of the commit
  parameter in three multi-argument setters, and mangled the indentation of a
  fourth. Grep for the specific failure mode afterwards, then read the diff.

---

## 8. Open questions for the maintainer

Do not resolve these unilaterally; they are recorded in `DESIGN_NOTES.md` §12
and `RELEASE_HISTORY.md`.

- **D1** — README legal note vs. provenance statements in source comments.
  *Explicitly deferred by the maintainer. Do not act on it.*
- **D2** — Should summer/winter head sprites actually differ? They are
  currently byte-identical.
- **D3** — Do people become a fully customisable category or stay ambient?
- **D4** — ~~Is the `MULTIPLY` tint colour trade-off acceptable on a real device?~~
  **ANSWERED, and the answer is yes.** Measured, not judged: under `MULTIPLY` the skyscraper
  window grid is authored as grey 234 on a 255/244 wall, so a window is always 8.2% darker than
  its wall *in proportion*. Converting that to CIELAB against real tints gives the contrast the
  eye actually gets:

  | tint | ΔL* wall vs window |
  |---|---|
  | white / very light | 7.3 / 7.0 |
  | neutral grey `#808080` | 4.2 |
  | **saturated red `#E53935`** | **4.1** |
  | pure red / pure blue | 4.4 / 3.0 |
  | the Big City theme's own default `#454B57` | 2.7 |
  | very dark `#1A1A2E` | 1.2 |

  A just-noticeable difference is around ΔL* 1. **Saturated colours are not the weak case** --
  they land at 4.1, well above the theme's own shipped default of 2.7 -- and this was confirmed on
  the emulator: at `#E53935` the window grid reads clearly. The only regime that loses the grid is
  a *very dark* tint, which is inherent to multiplying and is arguably the right look for a dark
  building at midday. Do not "fix" this with an asset change; it would harden the light end, which
  is fine, to rescue a dark end that is behaving as designed.

  **v4.12 did change `skyscraper_wall_lit`, and it is not this.** That change made the tower's
  window grid a white mask so the renderer could tint it cool by day and warm at night, which is a
  question about *what colour a window is* and had nothing to do with contrast. The wall's own
  baked grid, the one measured above, is untouched and the guidance here still stands.
- **D5** — When to take the dependency upgrade?
