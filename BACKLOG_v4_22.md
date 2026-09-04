# BACKLOG_v4_22.md — what v4.22 decided, and what it left open

**Replaces `BACKLOG_v4_21.md`.** That file's resolved and documented items are settled and not
restated; what it left open is carried forward below unchanged (items 18, 20, 25, 30) or closed
here with its outcome (item 29). Numbering continues from it: item 31 is new to this release.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED**
(decided against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not
rediscovered), or **OPEN** (left undone on purpose, with what closing it would take).

---

## Summary

| item | what | outcome |
|---|---|---|
| 18 | An unreadable custom theme loses the whole store | **OPEN**, carried forward from v4.20 unchanged |
| 20 | The GL goldens' emulator side is a derivation, not an observation | **OPEN**, carried forward from v4.21 unchanged — still a one-run debt |
| 25 | The palm is the odd tree out | **OPEN**, carried forward from v4.21 unchanged — an artistic pass, not a defect |
| 29 | The pavement focus does not catch a change in how many people there are | **RESOLVED** — gates derived for all four v4.22 regressions, each between its measured floor and its measured weakest signal |
| 30 | Hand-maintained counts still in the current-state documents | **OPEN**, carried forward unchanged — one at a time, each with its measurement |
| 31 | Every v4.22 gate's floor is an observation on one device | **OPEN**, and deliberately so — it cannot be closed on this machine |

---

## 18, 20, 25, 30 — carried forward

Unchanged from `BACKLOG_v4_21.md`; see that file for the full accounts. Nothing in v4.22 touched
the custom-theme store's error path (18), the GL goldens' emulator side (20), the palm (25), or
the undated counts (30).

## 29 — the pavement focus, and every new setting of this release, now has a gate

**RESOLVED, with the derivation the item asked for.** Closing it meant choosing a metric, and the
metric chosen is the one the item's own text prescribes: for each regression that must fail,
measure it, measure the noise floor on the same rectangle, and put the gate midway, with both
numbers written beside it.

The floor, first (v4.22 Fase 1 — MISURATO on the OnePlus 6T): frames of the same scene rendered
by separate instrumented executions, across a process restart and a device reboot, are
**byte-identical** — 0.0000% on the pavement band, on the road band, and on the whole frame. The
Canvas golden pipeline on this device is bit-deterministic, so every gate's floor is zero and the
gate is half its weakest signal.

The gates (all in `SettingsGates`, asserted as extra or scene foci with derived per-focus limits
— `GoldenFocus.maxDifferingFraction`, default unchanged for every pre-existing focus — and
re-measured on every run by the two `…StandsBetweenFloorAndSignal` tests):

| regression | frame | rectangle | weakest signal | gate |
|---|---|---|---|---|
| people density ignored | `people-single` | `PAVEMENT` | (misurato — vedi report v4.22) | metà del segnale |
| day car count ignored | `traffic-day-sparse` (new) | road band | (misurato) | metà |
| night car density ignored | `traffic-night-quiet` (new) | road band | (misurato) | metà |
| business hours ignored | `shops-closed-night` (new) | facades band | (misurato) | metà |

The three new frames exist because no committed frame exercised any non-default value of these
settings — which is also why "no golden moved" was the honest phase-2 verdict. Each new scene's
inputs are derived, not habitual, and `GoldenUniquenessTest` (active, no allowlist) is the proof
that each new PNG carries information none of the others already carried.

## 31 — the gates' floor is one device's observation

**OPEN, and it cannot be closed here.** The 0.0000% floor under every v4.22 gate was observed on
one OnePlus 6T: four separate instrumented executions, a process restart and a device reboot,
byte-identical PNGs. That is strong, and the Canvas golden path is software rendering, so the
same determinism elsewhere is *plausible* — but plausible is a derivation, not an observation,
and item 20 already records how that distinction bites. Every gate derived from that zero
inherits the debt: on a device or Skia build where the floor is not zero, a gate at half the
weakest signal may sit closer to the floor than the derivation assumed.

Closing it takes what closing item 20 takes: one run of the golden capture on a second
environment, byte-comparing PNGs across two executions there. If the floor is zero there too,
this item closes with a measurement; if it is not, the gates get re-derived against the measured
floor — against evidence, not against an estimate. Until then the risk is bounded the same
one-directional way item 20's is: the device every visual judgement is taken on is exact.
