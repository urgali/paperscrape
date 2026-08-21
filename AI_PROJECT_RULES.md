# AI_PROJECT_RULES.md

Permanent, transferable rules for any AI assistant or contributor working on
PaperScrape. These rules survive across sessions and conversations. They are
not a task list — they describe how work on this project must be done,
regardless of what the current task is.

**Language of this file, and of every file in this repository: English.**

---

## 1. Source of truth

1.1. **PaperScrape's current state in this repository is always the source of
truth.** Not memory, not a previous conversation, not documentation that may
have drifted. When documentation and code disagree, the code wins and the
documentation is fixed.

1.2. Before making any claim about how the project behaves, verify it against
the actual files. Claims like "this file does X" must come from reading the
file in the current session, not from recall.

1.3. `README.md`, `ROADMAP.md`, `CHANGELOG.md` and the documents in this set
(`ARCHITECTURE.md`, `DESIGN_NOTES.md`, `RELEASE_HISTORY.md`) are inputs to be
read at the start of any significant task, and outputs to be updated at the
end of it.

---

## 2. PaperScrape is a standalone project

2.1. **PaperScrape has no reference project, and no work on it may acquire one.**
Until v3.0 an external product was consulted for behavioural questions -- relative
proportions, depth ordering, which categories were recolourable, animation
cadence -- and its name was a forbidden string that had to be scanned for before
every release. That dependency is closed. Every question those comparisons used
to answer now has an answer inside this repository, in `DESIGN_NOTES.md` and in
the code's own comments.

2.2. **Design decisions stand on their own.** A comment must describe what the
code does and why, in terms a reader with nothing but this repository can act on.
Write `// Depth band 0.27-0.375 keeps objects clear of the road`, never
`// because product X does it this way`. An appeal to an outside product is not
a reason; it is a reason someone else had, which nobody here can check.

2.3. Do not introduce a new external reference -- decompiled, downloaded or
otherwise -- as an input to a fix or a feature. If a question genuinely cannot be
answered from this project, from Android's own documentation, or from looking at
the app running, raise it with the maintainer rather than resolving it against
somebody else's product.

2.4. All assets shipped in this repository are original work produced by this
project's own asset pipeline (`tools/assets/`). Nothing is dropped in, traced or
pixel-matched from anywhere else.

2.5. The **visual identity** is `Paper Cutout 2D`: flat, two-dimensional,
cut-paper shapes, simple and clean, no 3D perspective and no rendering style
foreign to it. The **app's UI** is Material 3. Those two sentences are the whole
of the style rule, and neither of them needs an outside product to be understood.

2.6. Remember that `release-notes/*.md` are **published**: the release workflow
uses them as the GitHub Release body, and the in-app update dialog renders that
same body. Text placed there reaches end users.

---

## 3. Historical references

3.1. `CHANGELOG.md` and the pre-v2.0 files under `release-notes/` are a record of
what was done at the time, including work that was done by comparison with an
external product. **They are history and are left as written.** Rewriting a
published release note to say something other than what it said would be
falsifying the record, which is worse than the reference being visible in it.

3.2. `RELEASE_HISTORY.md` is the same: earlier entries describe earlier reasoning
and stay as they are. New entries follow section 2.

3.3. What was removed in v3.0 is every reference that was **operational** -- a
comment presenting an outside product as the authority for a current design
decision, a rule requiring material from it, a scan the release depended on.
Nothing that only *records* the past was touched.

3.4. If a residual reference is found that looks legal rather than editorial in
nature, raise it with the maintainer instead of deleting it unilaterally.

---

## 4. Code modification rules

4.1. Before modifying important code, inspect its dependencies, its call sites
and its side effects. Grep for every usage; do not assume a function has one
caller.

4.2. **Always look for root causes rather than fixing symptoms.** If a symptom
is fixed with a constant adjustment, state explicitly in the commit and in
`RELEASE_HISTORY.md` that it is a symptom-level fix and why the root cause was
not addressed.

4.3. Do not assume a recently modified area should be left alone. If the right
fix touches recently changed code, say so and ask for confirmation rather than
silently routing around it.

4.4. Avoid temporary workarounds when a correct architectural or logical fix is
available. If a workaround is genuinely the right call for now, record it in
`RELEASE_HISTORY.md` under known limitations, with the condition under which it
should be revisited.

4.5. Evaluate every significant change for side effects and regressions, and
name the specific regressions considered — not a generic assurance.

4.6. When a change may affect multiple components, analyse the integration
points first.

4.7. Do not introduce unnecessary redesigns. Preserve the existing artistic
direction and existing architecture unless a change is necessary or explicitly
requested.

4.8. Do not stop at the explicitly reported problem. Look for related
inconsistencies produced by the same system, and report them even when they are
out of scope for the current change.

---

## 5. Rendering and performance rules

5.1. **Nothing may be allocated per frame in a draw path.** No `Paint`, `Path`,
`Shader`, `Random`, `PorterDuffColorFilter`, list, array, sorted copy, boxed
value or `String` may be created inside a function that runs once per frame.
Allocate once, reuse, and invalidate explicitly.

5.2. **Scene layout is state, not a per-frame computation.** Candidate
positions, seeds and ordering must be computed when their inputs change (theme,
customisation, screen size) and cached until then. The existing star field and
hill-silhouette caches are the reference pattern to follow.

5.3. A per-candidate random value must be derived from a **stable per-candidate
seed**, never from a position in a shared RNG stream. A shared stream makes
every candidate's appearance depend on how many earlier candidates were
filtered out, so changing a density slider reshuffles the whole field instead
of adding or removing members of it.

5.4. **Unbounded accumulators must never be consumed as `Float`.** Time and
scroll accumulators must be accumulated as `Double` and wrapped into a bounded
range before being converted for rendering. An unbounded `Float` accumulator
loses precision within days of continuous use and eventually stops advancing
altogether.

5.5. Off-screen culling must be derived from the actual viewport dimensions and
the object's own bounding box, never from a hardcoded pixel constant.

5.6. Bitmaps are decoded once and cached, but the cache must respond to
`onTrimMemory`/`onLowMemory`. A live wallpaper that holds tens of megabytes of
bitmaps is a preferred victim of the low-memory killer.

5.7. Prefer a cheaper per-frame technique over a visually equivalent expensive
one. Blit a pre-rendered bitmap rather than re-walking an antialiased `Path`;
translate a cached `Path` rather than rebuilding its control points.

5.8. Any change to the render loop, allocation behaviour or caching must be
measured or reasoned about explicitly, not asserted.

5.11. **Boxing is an allocation.** A `Map<Int, …>` boxes its key on every
lookup, and identifiers such as resource ids fall outside `Integer`'s
small-value cache, so a boxed map on a draw path allocates per call. The
allocation happens inside `valueOf`, not as a `new` at the call site, so a
`javap` scan of the calling method will not show it — check for `valueOf` as
well as `new` when auditing a hot path.

5.12. **Cache eviction is a lifecycle concern, not a size concern.** A cache of
re-derivable data must respond to `onTrimMemory`. Map each level explicitly:
the `TRIM_MEMORY_*` values are **not ordered by severity** (`UI_HIDDEN` is 20,
above `RUNNING_CRITICAL` at 15, but is the mildest of them), so a threshold
comparison will mistake a routine signal for an emergency. Prefer dropping
references to `recycle()`, and never evict while something may still be drawing
from the cache.

5.10. **Never narrow an unbounded accumulator to a narrower type.** Time and
scroll accumulators grow without limit; converting one to `Float` before using
it reintroduces the precision cliff the wider type was chosen to avoid. Do the
arithmetic in the wider type and narrow only the *bounded* result — after the
`sin`, the modulo, or the integer index. Prefer this to wrapping the
accumulator: a wrap is only seamless when the period is a whole number of cycles
for every consumer, and consumers whose rate is derived at runtime make that
impossible to guarantee.

5.9. **Classify a configuration change before acting on it.** A change that
only affects how existing objects *look* must never discard the state of those
objects. Only the fields that decide which objects exist may trigger a rebuild,
and the comparison that decides this must be field-by-field rather than a hash
— a hash collision would silently skip a rebuild the scene needed. Where two
kinds of runtime state can be rebuilt independently (static objects and cars),
compare and rebuild them independently, so a change to one does not restart the
other.

---

## 6. Asset rules

6.1. **Every shipped asset must be reproducible from a generator committed to
this repository.** A PNG whose only source is the PNG itself is a dead end: it
cannot be re-derived, re-scaled, re-coloured or corrected without manual pixel
editing.

6.2. Every asset must have declared metadata: nominal size, content bounding
box, anchor point, scale convention, category, and whether it is runtime-tinted
or fixed-art.

6.3. Assets must be trimmed. Transparent padding is memory that is decoded,
held and blitted for no visual result. Padding is only acceptable when it is
load-bearing for alignment, and then it must be documented as such.

6.4. Assets must obey the project's sprite grid (see `DESIGN_NOTES.md`). An
asset whose pixel dimensions do not fit the grid is a bug in the generator, not
a special case to be worked around at draw time.

6.5. **No two shipped assets may be byte-identical.** If two variants are meant
to differ, a test must assert that they do. If they are meant to be the same,
ship one file and reference it twice.

6.6. Before replacing or regenerating an asset, determine whether the actual
problem is the asset or the code that positions, scales, composes or renders
it. Regenerating an asset to compensate for a positioning bug hides the bug and
guarantees it will recur for the next asset.

6.7. Before touching an asset, inspect: dimensions, aspect ratio, transparent
padding, bounding box, anchor point, visible content bounds, scaling, alignment
and consistency with related assets.

6.8. After creating or modifying an asset, **render it and look at it** against
a representative background before treating it as final. Reading the generator
source is not verification.

---

## 7. Scale, positioning and perspective rules

7.1. There must be **one source of truth** for how a depth value maps to a
screen position and to a scale factor. Categories may have their own base
scale; they may not have their own copy of the mapping.

7.2. There must be **one sprite scale convention**. If a second convention is
genuinely unavoidable, the convention must be carried in the asset's metadata,
not selected by which helper function the caller happens to use.

7.3. **Never fix a size or alignment problem with a per-asset constant when the
real fault is the shared perspective system.** A per-asset patch fixes one
object and leaves the mechanism intact to break the next one. If a per-asset
constant is genuinely correct, document why in `DESIGN_NOTES.md`.

7.4. Every new scene object category must be integrated into the shared depth,
ground-anchoring and parallax systems from the start. An object positioned by a
fraction of screen height is outside the scene and will desynchronise from
everything else the first time the terrain geometry changes.

7.5. Objects that share a ground plane must share an anchoring rule. Objects
that appear in front of or behind one another must derive that ordering from
their depth value, not from call order.

---

## 8. UX / UI rules

8.1. Material 3 governs the **app's UI language** (settings screen, dialogs,
controls). It does not govern the wallpaper's illustration style. Never convert
the wallpaper's original artwork into generic Material graphics.

8.2. If a Material 3 colour scheme is used, it must be defined **completely**.
A partially defined scheme silently falls back to Material's baseline palette
for every undefined role, which produces off-brand colours in controls,
containers and dialogs.

8.3. User-visible strings belong in `strings.xml` and are referenced via
`stringResource`. Hardcoded literals in Compose are not localisable and are
treated as a defect.

8.4. Continuous controls (sliders) must not write to persistent storage on
every value change. Hold the in-flight value in local UI state and persist on
interaction end, or debounce the persistence path.

8.5. A settings change must never reset unrelated running animation state.

8.6. Respect touch target minimums, contrast requirements and RTL. Keep
spacing, hierarchy and component sizing consistent across screens.

---

## 9. Android platform rules

9.1. Work to senior Android engineering standards in rendering, performance,
CPU/GPU usage, memory, lifecycle, Gradle, compatibility, testing, CI/CD,
maintainability and regression prevention.

9.2. Respect the wallpaper lifecycle. Stop timers, location updates and
coroutines when the surface is not visible or the engine is destroyed. Never
leak an `Activity` context into a process-lifetime cache.

9.3. All network access is off the main thread, has explicit connect and read
timeouts, and fails silently into a no-op rather than into a user-visible
error.

9.4. Persisted user data must carry a schema version so that a future format
change can be migrated rather than discarded. Custom themes use
`CUSTOM_THEME_SCHEMA_VERSION`, with `migrateCustomThemeJson` as the single
registration point for migration steps. Two rules follow from that:

- a payload from a **newer** schema than the running build is read
  best-effort, never rejected — refusing it would destroy every saved theme on
  an app downgrade;
- every migration step ships with a test that loads a fixture of the old shape
  and asserts the migrated result.

9.5. `minSdk` is 26. Do not use APIs above it without a guarded fallback.

9.6. Prefer no new dependency. When one is genuinely required, justify it
against the existing toolset.

---

## 10. Git, CI and hidden files

10.1. **Never delete, rename or recreate hidden files or directories without
explicit justification and impact analysis.** Always check for: `.gitignore`,
`.github/`, workflow files, and anything else beginning with `.`.

10.2. After any operation that moves, copies, archives or restructures the
project, verify that every hidden file and directory still exists and is
unchanged.

10.3. `CLAUDE.md` is local-only and must be listed in `.gitignore`. It must
never be committed or tracked by Git.

`CLAUDE.md` is intentionally included in project ZIP archives supplied to Claude
Code so that future sessions and accounts can recover the local Claude
instructions.

Its ignored/untracked status must be verified with Git — never assumed from
the presence of a line in `.gitignore`.

The presence of `CLAUDE.md` inside a project ZIP is intentional and is not a
Git-tracking exception.

10.4. GitHub Actions workflows must exist, be valid, and match the project.
Verify this rather than assuming it.

10.5. Actions are pinned to full commit SHAs. Keep them pinned.

10.6. Release signing secrets are consumed only by the release job. Never
introduce a path that exposes them to pull-request or fork builds.

10.7. `debug.keystore` is deliberately committed and must stay committed — it
carries the standard public debug credentials and exists so every build is
signed with the same certificate. This exception must never be extended to a
release keystore.

---

## 11. Versioning and releases

### 11.A Two numbers, two jobs

11.1. **`versionName` names the release; `versionCode` counts installs.** They are
not the same number and must not be tied together.

- **`versionName`** — what a user sees, and what a Git tag names. Semver
  `MAJOR.MINOR`: `1.0`, `1.1`, `2.0`.
- **`versionCode`** — Android's own monotonic install counter. It increments by
  exactly one per release and carries no release semantics: `1.0` → 1, `1.1` → 2,
  `1.2` → 3, `2.0` → 4.

Tying them together is what the previous rule got wrong. It required the tag's major
number to equal `versionCode`, which made only integer tags expressible and forced
the install counter to answer a question — "which release is this" — that belongs to
`versionName`.

11.2. **Change the Android version only when a release actually ships.** A working
session, a beta ZIP or a documentation pass does not advance it. When a release does
ship, both numbers move: `versionName` to the new semver, `versionCode` up by one.

### 11.B Choosing the next version

11.3. **Never infer the next version from conversation memory.** The project may be
continued from a different session or a different account, so memory is not evidence.
Before preparing any release:

1. read `versionName`/`versionCode` in `app/build.gradle.kts`;
2. inspect the existing Git tags and GitHub releases if the context can reach them;
3. otherwise read `RELEASE_HISTORY.md` and `release-notes/`;
4. take the next unused semver, and `versionCode + 1`.

11.4. **Versions are immutable and never reused.** Never overwrite an existing tag or
release.

11.5. One version per release, used consistently across: the release ZIP filename, the
`RELEASE_HISTORY.md` heading, `release-notes/vMAJOR.MINOR.md`, `versionName`, and the
Git tag.

### 11.C Tags and releases

11.6. **The tag is `vMAJOR.MINOR` and must equal `versionName`.** CI reads
`versionName` out of `app/build.gradle.kts` and fails the release if the tag disagrees.

| Tag shape | Meaning | GitHub |
|---|---|---|
| `vMAJOR.MINOR` — `v1.0`, `v1.1`, `v2.0`, `v2.1`, `v2.2`, `v2.3`, `v2.4`, `v2.5`, `v2.6`, `v2.7`, `v2.8` | Stable release | published, marked latest |

11.7. **There is no pre-release tag form yet.** Any tag that is not `vMAJOR.MINOR` is
rejected. A beta syntax will be added when it is needed rather than guessed at now;
the workflow's `prerelease` branch is left in place so adding one is a change to the
tag step alone.

11.8. **Releases are cut by pushing a tag, never by merging.** Merging to `main` builds
and tests only.

11.9. **Every published APK is signed with the same release key.** A differently-signed
APK cannot be installed over an existing one, which would strand whoever installed the
other.

11.10. **The in-app updater does not offer betas, and must not be changed to.**
`UpdateChecker` parses a release's version with
`tagName.removePrefix("v").toIntOrNull()`, so `v73.1` yields `null` and the entry
is skipped. Beta testers install manually from GitHub and are still offered the
next stable release when it appears. This began as an accident of the parser; it
is now a deliberate property and a test-worthy one.

11.11. Every release needs a `release-notes/<tag>.md` in plain,
user-facing language — `release-notes/v73.md` for a stable release,
`release-notes/v73.1.md` for a beta. It becomes the GitHub Release body, and for
stable releases the in-app update dialog text. CI prepends a "Beta /
pre-release" banner automatically for dotted tags; do not write one by hand.

11.12. **Do not produce an APK as the deliverable of a release** unless
explicitly requested. Ensure instead that the repository contains everything
needed for the Git/GitHub Actions build to produce it.

11.13. **Retired in v3.0.** A release used to require a global search for an
external product's name before it could be called complete. PaperScrape is
standalone (section 2) and the scan has nothing left to find, so it is no longer
a release gate. What replaces it is section 2.3: do not acquire a new external
reference in the first place.

---

## 12. Verification and testing

**This section is the single authoritative statement of verification policy.**
`CLAUDE.md` gives the commands and session mechanics; where the two appear to
differ, this section governs.

### 12.A Ground rules

12.1. **Never claim a capability or tool that has not been verified in the
current environment.** The environment resets between sessions; a tool that
existed last time may be absent now.

12.2. Before Android build or test work, verify the presence of: JDK, Android
SDK, build tools, Gradle, and any analysis tool to be used. Install only what is
genuinely necessary and compatible.

12.3. **Never describe as observed anything that was not observed.** No emulator
or device is available in this environment, so visual behaviour, tactile
behaviour, battery and thermal behaviour are *never* Claude-verified. State them
as outstanding, not as confirmed.

12.4. Read the warnings, not just the exit code.

### 12.B Verification levels

Every change is delivered at one of three levels. State the level and the reason
for it in the release report (12.E).

**Level 1 — Documentation-only.** The change touches only non-executable
documentation (`ROADMAP.md`, `RELEASE_HISTORY.md`,
`ARCHITECTURE.md`, `DESIGN_NOTES.md`, `AI_PROJECT_RULES.md`, `CLAUDE.md`,
`README.md` and similar).

Do **not** run `test`, `lintDebug` or `assembleDebug`. Run instead:
- documentation consistency, including between this file and `CLAUDE.md`;
- internal references;
- Git state and `.gitignore`;
- hidden files intact;
- release ZIP completeness.

**Level 2 — Normal code change. This is the default.** The change touches Kotlin
or Java source, tests, rendering or engine logic, ordinary resources or assets,
or app behaviour.

Run:
- `./gradlew test`;
- `./gradlew lintDebug`;
- static or bytecode checks relevant to what changed (for example an allocation
  audit when a draw path was touched);
- mutation testing where 12.D says it applies;
- release ZIP completeness.

Do **not** run `assembleDebug`.

**Level 3 — High-risk / release-candidate.** Escalate to this level when the
change touches Gradle or build configuration, `AndroidManifest.xml` or core
Android integration, CI workflows in any meaningful way, application or engine
lifecycle, critical rendering paths, or the asset pipeline; when it is a large
architectural change or otherwise carries high regression risk; when it is
declared a release candidate or milestone; or **whenever the maintainer asks for
full verification**.

Run everything in Level 2, plus:
- `./gradlew assembleDebug`;
- extraction of the release ZIP into a clean directory and a full build and test
  run from that copy alone.

12.5. **When the level is uncertain, escalate.** Choosing Level 3 costs time;
choosing Level 2 for a Level 3 change costs correctness.

### 12.C Build policy

12.6. **`./gradlew assembleDebug` is NOT a default verification step. It is an
escalation / high-risk verification step unless explicitly requested.**

Skipping `assembleDebug` in a normal session is **not** a gap, provided every
check required by the chosen level was performed. Do not apologise for it and do
not describe the delivery as unverified on that basis alone.

12.7. Compilation is still proven at Level 2: `./gradlew test` compiles the main
source set. What `assembleDebug` adds is packaging, resource linking and dexing.

12.8. **Clean-extraction rebuild is a Level 3 step**, not a routine one. ZIP
*completeness* verification remains mandatory at every level (14.7).

### 12.D Tests and mutation testing

12.9. Deterministic, pure logic (astronomical calculations, date rules,
serialisation round-trips, geometry helpers, candidate systems) must have unit
tests. A CI job that runs zero tests provides false assurance.

12.10. **Mutation testing is required when, and only when, it would tell you
something.** Apply it when new pure, testable logic is introduced; when critical
existing logic is changed; or when a new test suite needs to be shown to have
teeth.

Do not apply it to documentation-only changes, mechanical refactors that
introduce no new logic, asset-only or purely cosmetic changes, or trivial
changes where it would add nothing.

12.11. **A new test is not trusted until it has been shown to fail.** Break the
code it covers deliberately, confirm the test catches it, then revert. A suite
that passes on first run may be asserting nothing.

If a mutation does **not** produce a failure, that is a finding about the test,
not a clean result: report it and replace the mutation with one that targets the
invariant.

12.12. Assert behavioural contracts rather than exact magic numbers wherever the
value is tuned rather than defined. A test that fails whenever a curve is
re-tuned trains people to ignore it.

### 12.E Division of verification, and the release report

12.13. **Claude-side verification** is everything above that Claude actually
ran. **Maintainer-side verification** is what only the maintainer can do, and it
normally comprises: local APK build, installation on a device, real-device
visual verification, and practical CPU, battery and thermal observation.

Every release must separate the two explicitly. Never present a maintainer-side
item as done.

12.14. Every release ends with this report:

```
Release identifier:
Verification level:            1 / 2 / 3
Reason for the level:
Tests run:
Lint run:
APK build run:                 yes / no
Static / bytecode checks:
Mutation testing:              yes / no
ZIP verification:              yes / no
Clean build from extracted ZIP: yes / no
Maintainer-side verification required:
Release identifier verified unique: yes / no
```

When `assembleDebug` was not run, state verbatim:

`assembleDebug intentionally skipped under normal verification policy.`

and record that the APK build remains a maintainer-side verification.

---

## 13. Mockups and visual approval

13.1. For any significant visual or UX change, produce a visual
mockup or prototype and obtain the maintainer's approval **before**
implementing the final code or assets.

13.2. Small technical fixes with no visual impact do not require a mockup.

13.3. When in doubt about whether a change is visually significant, treat it as
significant and ask.

13.4. The full approval process is: inspect current state → propose change →
create mockup → obtain approval → implement → verify visually → record the
decision in `DESIGN_NOTES.md`.

13.5. Elements listed as protected in `DESIGN_NOTES.md` require explicit
approval before any modification.

---

## 14. Documentation rules

14.1. Keep this document set current:
- `ROADMAP.md` — the operational plan; when a phase changes state
- `AI_PROJECT_RULES.md` — when a permanent project rule changes
- `ARCHITECTURE.md` — when architecture changes
- `DESIGN_NOTES.md` — when a visual or design decision is made
- `RELEASE_HISTORY.md` — for every release
- `CLAUDE.md` — local environment and working notes, never committed

`ROADMAP_OLD.md` was deleted at the v1.0 cleanup. It held the original
pre-release plan, which had been superseded phase by phase and was no longer
consulted; `RELEASE_HISTORY.md` carries the reasoning that still matters.


14.2. **When a new decision changes an existing rule, edit the existing rule.**
Never append a contradictory instruction alongside it.

14.3. The same applies to code comments. Do not stack a new comment block on
top of an outdated one; replace the outdated text.

14.4. Comments describe what the code does and why. They are not a changelog,
not a conversation transcript, and must not attribute requests or reports to
named individuals. Write the technical reason, not who asked for it.

14.5. Documentation must reflect what the project actually does today.
Aspirational descriptions belong in `ROADMAP.md`.
14.6. **`ROADMAP.md` is the authoritative operational plan for the project.**
At the beginning of every significant task, the assistant must read
`ROADMAP.md` and determine the current phase and next approved work. When a
phase is completed, blocked, reordered or materially changed, `ROADMAP.md` must
be updated before the release is considered complete. A release must never rely
on conversation memory as the authoritative project state.

14.7. **ZIP completeness verification is mandatory at every level.** Before a
release is considered complete, verify that every file on disk is present in the
release ZIP, that `.gitignore`, `.github/` and the other hidden files are intact,
and that `CLAUDE.md` is inside the ZIP while remaining untracked by Git. This is
distinct from the clean-extraction rebuild, which is a Level 3 step (12.8).

14.8. **The project must survive a change of session or account.** The project
documentation and release ZIP must contain enough information for a fresh
Claude account to resume the project without access to previous conversations.
The authoritative state must be stored in the repository/project files, not in
conversation history. Before a release is considered complete, the assistant
must verify that the next session can be started using the release ZIP and the
documented project state.

---

## 15. Working language

15.1. Conversation with the maintainer may be in Italian.

15.2. **The project is entirely in English**: source code, comments, class
names, function names, variable names, package names, namespaces, file names,
folder names, Git branches, Git tags, commit messages, README files, project
documentation, workflow files, resource names and internal technical
documentation.

15.3. Never introduce Italian text into the repository unless explicitly
requested.

15.4. User-visible text follows the project's current localisation strategy
(see `ARCHITECTURE.md` for its present state).
