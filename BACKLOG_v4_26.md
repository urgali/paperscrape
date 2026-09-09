# BACKLOG_v4_26.md — what v4.26 decided, and what it left open

**Replaces `BACKLOG_v4_25.md` for new items only.** Everything `BACKLOG_v4_23.md`,
`BACKLOG_v4_24.md` and `BACKLOG_v4_25.md` leave open is still open and is not restated here; all
four files stay in the repository root until their open items are closed. Numbering continues:
`BACKLOG_v4_25.md` reached item 65, so this file starts at 66.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). Every number below was measured on it in this pass unless it says otherwise.

---

## What v4.26 closed from `BACKLOG_v4_25.md`

| item | what | how it was closed |
|---|---|---|
| 61 | Seven length systems all called `_UNITS` | **CLOSED** — proposal **C** taken: `UnitFrameTest` scans every expression in `main`, `test` and `androidTest`, resolves each `_UNITS` constant to a declared frame, and fails any expression naming two frames without one of the five conversions. Shown to bite by reintroducing v4.25's real second defect into the tree. See item 68 for the half that stays open |
| 64 | Is the double golden regeneration per-release or per-device? | **CLOSED by the maintainer** — the double regeneration now runs only on the scenes whose frames changed. Written into the protocol in `SceneGolden`'s doc; the reasoning is repeated in item 69 |
| 65 | The golden suite would have gone green over a scene in which every person was redrawn | **CLOSED by derivation** — three new derived gates over the cloud band, the bird band and the water band, and the coverage re-measured. See item 69 |

---

## Summary

| item | what | outcome |
|---|---|---|
| 66 | The `perf` build type is rebuilt by hand every session, and this time it escaped into a published release | **OPEN** — a question for the maintainer, with both arguments and a proposal |
| 67 | The `_UNITS` frame rule sees Kotlin and not the generators | **OPEN** — the stated limit of item 61's fix; closing it is proposal A applied to `tools/assets` |
| 68 | The frame scanner reads statements, and a statement is not a scope | **DOCUMENTED** — three deliberate limits, none of which can hide the two shapes that have occurred |
| 69 | What the golden net covers now, measured | **RESOLVED** — three derived gates; two of them catch a family's total loss that the shared limit forgave |
| 70 | "+4.5 points of CPU for every PNG substituted" was the debug build talking | **RESOLVED** — a conclusion invalidated, and it applies to every future measurement |
| 71 | The GL reference frames were re-captured on PowerVR, and the Adreno gap is no longer measured | **OPEN** — a ratification: the artwork forced it, and nothing else could have passed |

---

## 66 — The `perf` build type is rebuilt by hand every session, and this time it escaped into a published release

**OPEN, and it is a question for the maintainer rather than a defect to fix.** Nothing here is
decided and nothing was changed except removing the build type from this release's tree.

### What happened

`app/build.gradle.kts` carried a build type called `perf`, with the comment
*"TEMPORARY, for the v4.25 CPU measurement only. Never committed, never in the ZIP."* It was in
the v4.25 delivery ZIP, and it is in the published `v4.25` tag on GitHub — read from the Releases
API, not from a document in the tree. The rule the comment states is a good rule and it was
ignored, not wrong; the block is removed from this release.

**The impact on what users have is nil**, and that is worth stating precisely rather than
reassuringly: CI builds `assembleRelease`, so no artefact anyone installed was ever built from
`perf`. What leaked is four lines of build configuration. There is nothing to do to the published
release.

### Why it keeps coming back

The build type is not an accident of v4.25. **Every session that runs the item-32 CPU protocol
needs it**, because a debug build is not what users run and a real release build cannot be signed
here. So every such session writes the same four lines by hand, measures, and deletes them again —
and the deletion is the step that failed once already.

This release adds a second reason to care. The "+4,5 points of CPU for every PNG substituted"
figure that shaped three rounds of concept work was an artefact of measuring on the **debug**
build; re-measured on a release-like one, substituting a sprite costs what repeating the same
measurement costs. **A measurement that is not reproducible produces conclusions that are not
either**, and hand-rebuilding the build type is exactly what makes it non-reproducible.

### The argument for committing it

- It signs with the already-committed `debug.keystore`, carries `applicationIdSuffix = ".debug"`,
  and holds no secret of any kind. There is nothing in it that a reader could misuse.
- It makes the item-32 protocol reproducible: two sessions measuring the same thing would be
  measuring the same binary, which they currently are not.
- It removes the hand-rebuild, which is the step that produced the leak. A rule enforced by
  remembering is a rule that fails on the session that forgets.
- The project already commits a build-time convenience of exactly this shape — the debug keystore —
  and for the same reason: reproducibility beats the tidiness of an absent file.

### The argument against

- It is a fourth build type that nothing in CI builds, so it is dead configuration for every reader
  who is not measuring CPU. Dead configuration is the class of thing `SpriteReachabilityTest` and
  item 57 exist to keep out of this project.
- `initWith(release)` plus a debug signing config is a build that looks like a release and is not
  one. Somebody could measure, or worse *ship*, the wrong thing — which is a bigger failure mode
  than the one committing it prevents.
- The leak was a review failure, and the fix for a review failure is a check, not a policy change.
  A test that fails when `app/build.gradle.kts` declares a build type CI does not build would have
  caught this in v4.25 and would keep catching it.

### A proposal

**Commit it, and add the check.** The two are not alternatives: the check is what makes committing
it safe. Concretely — keep `perf` in `app/build.gradle.kts` with a doc comment saying what it is
for and that it is never published; add a unit test asserting the exact set of build types the file
declares and that `perf` carries the debug signing config and the `.debug` suffix, so it cannot
quietly turn into something shippable; and record in the item-32 protocol that measurements are
taken on `perf` and quote the build type beside every number.

**Not decided here. This is the maintainer's call, after the release.**

---

## 67 — The `_UNITS` frame rule sees Kotlin and not the generator

**OPEN, and it is the stated limit of the fix rather than a new defect.** `UnitFrameTest` (item 61,
proposal C) scans `app/src/main`, `app/src/test` and `app/src/androidTest`. **The first of item 61's
two real defects was in Python** — `build_people_concepts.SEATED_HALF_BAND`, a bust length justified
against a car-unit seat pitch, which shipped a whole release of squashed heads.

A name-driven scanner cannot reach it as things stand, and the reason is worth writing down because
it is the same reason twice: `SEATED_HALF_BAND` **does not end in `_UNITS` and names no frame at
all**, so there is nothing for a frame table to resolve. The generator's other constants mostly do
declare their frame — `SEAT_PITCH_CAR_UNITS`, `SEATED_CLEARANCE_CAR_UNITS`,
`CAR_UNITS_PER_BUST_UNIT` — which is why the defect was in the one that did not.

Closing it is proposal **A** applied to `tools/assets`: rename every length in the generators to
`<QUANTITY>_<FRAME>_UNITS`, then point the same scanner at the Python sources with the same frame
table. The scanner is already frame-table-driven and language-agnostic in shape; what is missing is
the naming pass, and that is a change to files the app does not build, so it is cheap and separable.

---

## 68 — The frame scanner reads statements, and a statement is not a scope

**DOCUMENTED. Recorded so the next reader does not mistake a limit for a bug.**

`UnitFrameTest` propagates a frame through local `val` declarations, which is what lets it see the
shape v4.25's second defect actually had — a constant compared against a *local variable*. That
propagation is **per file and not per scope**: two functions in one file that each declare a `val`
of the same name share one entry, and the later one wins.

Three consequences, all of them deliberate:

- a `val` on its own declaration line does not compare with whatever an earlier, unrelated `val` of
  that name left behind — the declared name is excluded from its own statement, or every shadowed
  name would be a false positive;
- the branches of an `if`/`when` and the entries of a collection literal are **alternatives**, not
  terms of one expression, so the scanner cuts a statement at those boundaries. A mix that straddles
  a cut — `if (x) A_UNITS else B_UNITS + C_UNITS` — is seen only inside the branch it lives in;
- a conversion anywhere in the statement clears it, which is coarser than requiring the conversion
  to be applied to the right operand.

Each of those is a place a real mixed expression could hide. **None of them can hide the two shapes
that have actually occurred**, which is the bar this was built to, and a scanner that could not be
wrong in any of these ways would be a type checker — which is proposal B, and its cost is the
boxing audit `AI_PROJECT_RULES.md` 5.11 describes rather than the refactor.

---

## 69 — What the golden net covers now, measured

**RESOLVED, and the number is the deliverable.** `BACKLOG_v4_25.md` item 65 measured that a release
which redrew every person in the scene left **25 of 26** Canvas golden assertions green. v4.26
redraws the sky and the water, which are the two largest surfaces in the frame, and nothing watched
either of them.

Three derived gates were added, one per family this release touches — the cloud band, the bird band
and the water band — each placed by the v4.22 rule, between the measured noise floor and the
measured weakest regression that must fail, with both numbers written beside it in `SettingsGates`.
They hang off golden scenes that already exist, so they add assertions and no committed PNG.

The measurements, the coverage before and after, and the mutation that produced the "after" number
are in the v4.26 report. **No tolerance was moved and no number was lowered**, which is what item 65
required.

The double regeneration is now run only on the scenes whose frames changed (item 64, decided by the
maintainer). The reasoning is in `SceneGolden`'s doc: a newly non-deterministic scene is a changed
scene by definition, so restricting the check to changed scenes keeps the release-introduced
non-determinism it exists to catch, while a scene whose bytes did not move has already demonstrated
its determinism by matching its committed golden.

---

## 70 — "+4.5 points of CPU for every PNG substituted" was the debug build talking

**RESOLVED, and it invalidates a conclusion rather than a measurement.**

Three rounds of concept work were shaped by a figure measured on the **debug** build: replacing any
sprite PNG appeared to cost about 4.5 points of process CPU, whatever the replacement was and
however many vertices it carried. Re-measured on a release-like build, **substituting a sprite costs
what repeating the same measurement costs** — the difference is inside the run-to-run spread.

The number was real; what was wrong was reading it as a property of the artwork. It is a property of
the debug build, and it applies to **every future measurement**: a figure taken on a debug build is
a figure about a debug build. See item 66 for why the release-like build type keeps having to be
rebuilt by hand, and what to do about it.

---

## 71 — The GL reference frames were re-captured on PowerVR, and the Adreno gap is no longer measured

**OPEN, as a ratification rather than a defect.** The re-capture happened; what is open is whether
the maintainer accepts what it costs, and if not, what to do instead.

`BACKLOG_v4_25.md` item 56 said the three GL references portray people who no longer exist, and that
re-baselining them on this device's PowerVR driver "is a release of its own": it means re-capturing
all three, re-characterising the driver gap `GlDriverGapGuardTest` is calibrated on, and accepting
that the Adreno measurement is gone.

**v4.26 forced it.** The three GL scenes are `day`, `lake-busy` and `thunderstorm`, and this release
redraws the sky and the water, so all three Canvas frames changed — 17.1%, 23.9% and 13.0% of the
frame. Measured against the Adreno-authored references the GL suite read 6.31%, 9.51% and 3.192%
against limits of 3.00%, 3.00% and 0.500%. **No driver could have matched frames of a scene the
build no longer draws**, and raising a tolerance to make them pass is what `CLAUDE.md` §7 exists to
forbid. The maintainer's own instruction for this pass — GL untouched *if* the Canvas frames of
their scenes do not change — has its condition false here.

**What it costs.** The reference driver is now PowerVR GE8320. `GlDriverGapGuardTest` measures the
gap between the running driver and the committed frames, so on this device it now reads ~0, and the
cross-driver gap — 1.18 / 1.07 / 0.92% when it was characterised, 1.2-1.4% when v4.19 re-measured it
— is no longer being measured anywhere until the suite runs on another driver.

**Nothing is destroyed**: the Adreno frames are in `PaperScrape_v4_25.zip` and in the published
`v4.25` tag. What is gone is their usefulness against this artwork.

**What closing this would take**, if the maintainer wants the cross-driver measurement back: a
device with a second GPU vendor to run the suite on, and `GlGolden.EdgeDisplacement`'s
characterisation re-derived from that run. Per-driver golden sets were considered and rejected in
v4.20 — they double the maintenance and make "the golden" ambiguous — and nothing here reopens that.
