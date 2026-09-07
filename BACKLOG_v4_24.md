# BACKLOG_v4_24.md — what the v4.24 documentation review found and did not fix

**Replaces `BACKLOG_v4_23.md` for new items only.** Everything `BACKLOG_v4_23.md` leaves open is
still open and is not restated here; that file stays in the repository root alongside this one
until its open items are closed. Numbering continues from it: `BACKLOG_v4_23.md` reached item 48,
so this file starts at 49 and uses short numbers 1-n only in its own summary column below.

v4.24 was a documentation review with one executable change — `versionCode` and `versionName`.
Everything below was found while reading, was out of that mandate's scope, and is written down so
it is not rediscovered.

| # | Item | Kind |
|---|---|---|
| 49 | `tools/assets/reports/runtime-inventory.md` is stale | measurement |
| 50 | Nineteen Kotlin compiler warnings, and nothing reads them | code quality |
| 51 | Eighteen `UnusedResources` lint warnings | code quality |
| 52 | `CONTRIBUTING.md` tells contributors to use an emulator | documentation |
| 53 | `DESIGN_NOTES.md`'s "last fully verified against v74" line | documentation |
| 54 | The lookup-group and margin-carrying sprite counts have no definition to count against | measurement |
| 55 | Nothing in the tree learns that a release was published | process |

---

## 49. `tools/assets/reports/runtime-inventory.md` is stale, and `ARCHITECTURE.md` used to point at it as authoritative

**Measured 2026-09-07.** The committed report records **24 byte-identical groups** and **30.25 MB**
decoded. The shipped set measures **0 groups** and **28.85 MB** across 266 PNGs — verified twice,
once by hashing every file and once by reading the PNG headers directly, and confirmed on the pair
the report names first (`person_boy_summer_head_window` and its `_skin2`, which are not identical).

It predates v4.20's retirement of the duplicate skin bases and v4.23's celestial re-render.
`ARCHITECTURE.md` now warns that the report is evidence of a run rather than a live view, but the
file itself should be regenerated:

```bash
cd tools/assets && python -m paperscrape_assets inventory
```

v4.24 did not do it because regenerating writes into `tools/assets/reports/`, and that pass was
authorised to change two lines of `app/build.gradle.kts` and nothing else. **Do it in the next pass
that touches assets, and commit the result** — the JSON alongside the markdown.

---

## 50. Nineteen Kotlin compiler warnings, and the build is green anyway

**Measured on the v4.24 build, 2026-09-07.** Four groups:

| count | warning |
|---|---|
| 11 | `Java type mismatch: inferred type is 'Nothing?', but 'String' was expected` — `CustomThemeData.kt` |
| 3 | the same against `File` |
| 4 | `TRIM_MEMORY_RUNNING_LOW` / `TRIM_MEMORY_RUNNING_CRITICAL` are deprecated |
| 1 | `Condition is always 'true'` |

The first fourteen are `org.json`'s platform types read through Kotlin's nullability — real, and
fixable by asserting or handling the null at each site rather than by suppressing. The deprecations
need a decision about what replaces those trim levels, which is a behaviour question and belongs to
a pass that can measure memory pressure. The last one is one line and should just be read.

`ARCHITECTURE.md` claimed **0** compiler warnings until this release, and nobody noticed because
nothing reads warnings when the exit code is zero — which is exactly what `AI_PROJECT_RULES.md`
12.4 exists to prevent. **Whoever closes this should also decide whether the build should fail on
new warnings**, because a count that only a human reads will drift again.

---

## 51. Eighteen `UnusedResources` lint warnings

Out of 29 lint issues, 18 are `UnusedResources`. v4.20 removed four genuinely unreachable sprites
and the dead table that kept eight more referenced, so these are what remains after that pass. They
need classifying one at a time — a resource referenced only from XML, a resource kept for a reason
that should be written down, or a resource that should go — and `SpriteReachabilityTest` is the
guard that already exists for the sprite half of the question. This is item 7 of the old "older
priorities" list, now in `ROADMAP.md`'s Deferred section, restated with a number.

---

## 52. `CONTRIBUTING.md:62` tells contributors to use the Android Studio emulator

Not wrong for an outside contributor on their own machine, and not a claim about this environment —
which is why v4.24 left it. But it sits two paragraphs from the project's own verification policy,
and `AI_PROJECT_RULES.md` 12.3 now says plainly that there is no emulator here and that verification
runs on a device. Decide whether `CONTRIBUTING.md` should say which of the two it is describing.

---

## 53. `DESIGN_NOTES.md` says "last fully verified against v74"

That line is now many releases old, and unlike a count it cannot be replaced by a command: it is a
claim that somebody read the document against the code and found it true. Either re-verify and
re-date it, or delete the claim — a stale verification date is worse than none, because it invites
trust the document has not earned. v4.24 reorganised that file without re-verifying its content, so
it deliberately did not touch the line.

---

## 54. Two sprite counts have no definition to count against

`ARCHITECTURE.md` used to say "44 sprites are selected from a lookup table" and "34 sprites carry
margin on purpose". `BACKLOG_v4_21.md` had already flagged both: the first was never re-measured
because the lookup-group boundary is not defined anywhere a script can read, and the second
disagrees with `SpriteCanvasConventionTest`, which measures 49 sprites not touching any edge — a
different metric, not a correction.

v4.24 applied 14.11 the only way it honestly could: it removed the two numbers and pointed at the
tool and the exclusion list instead. **The real fix is to define the two metrics well enough that
`validate` can print them**, at which point they become commands like the others.


---

## 55. Nothing in the working tree learns that a release was published, so the status documents rot in one direction

`ROADMAP.md` said "the last release the maintainer reports as published is v4.19" from the day it
was true. It stayed there while v4.20, v4.21, v4.22 and v4.23 were tagged and published, because a
delivered ZIP is written *before* publication and nothing writes back afterwards. v4.24 was planned
around that stale line and would have published a user-facing note asserting that four visible
releases did not exist.

The line is now replaced by the command that reads the Releases API, which is 14.11 applied to a
fact rather than to a count. That is the minimum fix, not the whole one. Worth deciding:

- whether a session should **always** read the Releases API before writing anything about
  publication state, and `AI_PROJECT_RULES.md` §11 should say so;
- or whether the maintainer, on publishing, updates one line in `ROADMAP.md` — cheap, but it is a
  hand-maintained fact, which is the category 14.11 exists to shrink;
- or whether `RELEASE_HISTORY.md` should carry, per release, a "published" field that a session
  fills from the API rather than from memory.

The API read costs nothing, writes nothing and needs no credential, so the first option is the one
this backlog recommends.
