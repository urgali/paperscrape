# Archived documents

Every file listed here used to sit in the repository root. **Source comments, tests and
older documents cite them by bare file name, not by path, so nothing was rewritten when
they moved: a bare name resolves here.** This index is where it resolves to.

Nothing in this directory is opening reading. Consult a file when the thing you are
working on points at it by name.

---

## Backlogs of closed releases

The backlog numbering is continuous across files: each backlog replaces its predecessor
and keeps the item numbers, so item 27 means the same thing wherever it is cited.

| File | Was in root until | What it holds |
|---|---|---|
| [`BACKLOG_v4_20.md`](BACKLOG_v4_20.md) | docs pass of 2026-09-07 | The v4.19 backlog, closed. Fifteen items with an outcome each, plus items 16 and 17. |
| [`BACKLOG_v4_21.md`](BACKLOG_v4_21.md) | docs pass of 2026-09-07 | What v4.21 decided and left open. Items 19, 20, 26, 27. |
| [`BACKLOG_v4_22.md`](BACKLOG_v4_22.md) | docs pass of 2026-09-07 | What v4.22 decided and left open. Items 29, 31–36. |

**`BACKLOG_v4_19.md` does not exist and is not a broken reference.** It was deleted
deliberately when `BACKLOG_v4_20.md` replaced it, and `BACKLOG_v4_20.md` says so in its
own first lines: *"Replaces `BACKLOG_v4_19.md`, and keeps its numbering — source comments
and older documents cite that file by item number, and every one of those numbers means
the same thing here, so the citations still resolve."* The sixteen citations of
`BACKLOG_v4_19.md` in the Kotlin sources and the asset tooling are therefore correct as
they stand: read them against [`BACKLOG_v4_20.md`](BACKLOG_v4_20.md).

The open backlogs stay in the repository root: **`BACKLOG_v4_23.md`** for the artwork and
renderer items, **`BACKLOG_v4_24.md`** for what the v4.24 documentation review found and left.

---

## Release and pass reports

| File | Was in root until | What it holds |
|---|---|---|
| [`V4_22_MIGRAZIONE_BV6600_REPORT.md`](V4_22_MIGRAZIONE_BV6600_REPORT.md) | docs pass of 2026-09-07 | The move from the OnePlus 6T to the Blackview BV6600: what the new device is, what it does not do, and which earlier numbers became historical. |
| [`V4_22_GOLDEN_BV6600_REPORT.md`](V4_22_GOLDEN_BV6600_REPORT.md) | docs pass of 2026-09-07 | Re-authoring the 24 Canvas goldens on the BV6600, and the per-region attribution that justified it. |
| [`V4_22_MISURA_CPU_REPORT.md`](V4_22_MISURA_CPU_REPORT.md) | docs pass of 2026-09-07 | The CPU measurement protocol, and the **OnePlus 6T** figures. Historical numbers: the device is gone. |
| [`V4_22_AUDIT_SPRECO_GL_REPORT.md`](V4_22_AUDIT_SPRECO_GL_REPORT.md) | docs pass of 2026-09-07 | The GL waste audit and the release-like build type. Also **OnePlus 6T** numbers; the method carries over, the figures do not. |
| [`V4_23_FASE1_CONCEPT_REPORT.md`](V4_23_FASE1_CONCEPT_REPORT.md) | docs pass of 2026-09-07 | The three celestial concepts photographed on the device. |
| [`V4_23_GIRO1B_REPORT.md`](V4_23_GIRO1B_REPORT.md) | docs pass of 2026-09-07 | Concept B chosen; halo variants and the two costs of the shadow. |
| [`V4_23_GIRO1C_REPORT.md`](V4_23_GIRO1C_REPORT.md) | docs pass of 2026-09-07 | The attached corona, the 40-vertex pumpkin, seven suns in a row. |
| [`V4_23_GIRO1D_REPORT.md`](V4_23_GIRO1D_REPORT.md) | docs pass of 2026-09-07 | V2 chosen and refined; the pumpkin redrawn; the star diagnosis. |
| [`V4_23_FASE2A_REPORT.md`](V4_23_FASE2A_REPORT.md) | docs pass of 2026-09-07 | The artwork freeze: two pumpkins and the sparkle, photographed. |
| [`V4_23_FASE2B_REPORT.md`](V4_23_FASE2B_REPORT.md) | docs pass of 2026-09-07 | The celestial family in production; the sprite-reach constant, not the radius. |
| [`V4_23_GIRO2C_REPORT.md`](V4_23_GIRO2C_REPORT.md) | docs pass of 2026-09-07 | The pumpkin-moon golden and the locked phases switch. |
| [`SANTA_CROP_REPORT.md`](SANTA_CROP_REPORT.md) | docs pass of 2026-09-07 | The `santa_sleigh_scene` crop and its origin compensation. |
| [`V4_25_REPORT.md`](V4_25_REPORT.md) | written here, 2026-09-08 | **The v4.25 release report** — one report for the release, as 14.9 requires, updated in place rather than joined by a second one. Where the second `PaperScrapeGlThread` comes from; the head block replacing the visible skin in two assertions; the tint gate closed at the blit; the three occupant defects (a head narrowed to fit a band read in the wrong unit, a face pointing at the boot, a seat pitch re-derived) and the pane-fill criterion **restored** to 50% rather than lowered; the same unit error found in two files; and the acceptance sheet that came out of all of it. Its first section says which delivered archive it supersedes and why. |

Seven reports for one release is the pattern `AI_PROJECT_RULES.md` 14.9 now forbids: from
here on a pass produces **one** report per release, not one per phase or per round — which
`V4_25_REPORT.md` is the first to do.

---

## Moved out of a document rather than out of the root

| File | Came from | Why |
|---|---|---|
| [`ROADMAP_HISTORY.md`](ROADMAP_HISTORY.md) | `ROADMAP.md`, docs pass of 2026-09-07 | The release-by-release account, the "Completed" list, and the two rows that had already been struck through (older-priorities item 5, deferred D10). Reproduced unchanged, with one exception declared in the v4.24 report: a missing code-fence delimiter was closed, because a fence is rendering rather than content. |
