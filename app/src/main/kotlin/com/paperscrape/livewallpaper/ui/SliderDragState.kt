package com.paperscrape.livewallpaper.ui

/**
 * The state machine behind a slider that persists its value only when the drag ends.
 *
 * Kept as pure functions, with no Compose or Android types, so the tricky part -- the handover
 * between the locally held in-flight value and the persisted value coming back through the
 * preferences flow -- can be unit tested directly.
 *
 * ### The problem this solves
 *
 * A slider bound straight to a persisted value has a full round trip in its feedback loop:
 * finger -> `onValueChange` -> DataStore write -> flow emission -> recomposition -> new thumb
 * position. Every intermediate drag position pays for a disk write, and the thumb only moves once
 * the value has come back, so it lags and fights the finger.
 *
 * Holding the value locally during the drag removes the loop entirely: the thumb follows the
 * finger at frame rate, and storage is written once, at the end.
 *
 * ### The handover, which is the part that is easy to get wrong
 *
 * Clearing the local value the instant the drag finishes makes the thumb snap back to the stale
 * persisted value for the few frames before the write lands. So the local value is kept until the
 * persisted value has actually caught up with what was committed. [shouldReleaseLocalValue]
 * decides that.
 *
 * The float comparison is exact rather than approximate on purpose: the committed value is the
 * same `Float` that was written, and it round-trips through DataStore unchanged, so the value
 * coming back is bit-identical. An epsilon would risk releasing early on a genuinely different
 * nearby value.
 */
internal object SliderDragState {

    /**
     * The value the slider should display: the in-flight drag value when there is one, the value
     * awaiting confirmation when a drag has just ended, and otherwise the persisted value.
     */
    fun displayValue(persisted: Float, inFlight: Float?, awaitingCommit: Float?): Float =
        inFlight ?: awaitingCommit ?: persisted

    /**
     * Whether a value should be written to storage when the drag ends.
     *
     * A drag that ends on the value it started from writes nothing: an accidental tap on the
     * track, or a drag returned to its origin, should not produce a write or a flow emission.
     */
    fun shouldCommit(persisted: Float, inFlight: Float?): Boolean =
        inFlight != null && inFlight != persisted

    /**
     * Whether the locally held value can be dropped now that [persisted] has arrived.
     *
     * True once the persisted value equals what was committed -- the write has completed its
     * round trip and the two sources agree, so the local override is no longer doing anything.
     *
     * Also true when [awaitingCommit] is `null`, which covers the case of an external change
     * (a theme switch, a reset, a random-theme roll) arriving while nothing is pending.
     */
    fun shouldReleaseLocalValue(persisted: Float, awaitingCommit: Float?): Boolean =
        awaitingCommit == null || persisted == awaitingCommit
}
