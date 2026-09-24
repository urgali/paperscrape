package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What this covers, and what it does not, stated before the first assertion.**
 *
 * The update notification's decisions, asserted as arithmetic. It says nothing whatever about a
 * phone.
 *
 * ### Why the honesty paragraph is not boilerplate here
 *
 * `android.permission.POST_NOTIFICATIONS` arrived in **Android 13 / API 33**. This project's only
 * device is a Blackview BV6600 on **Android 10 / API 29**, where that permission **does not exist**
 * — `pm list permissions` returns eighteen notification-related permissions on it and this is not
 * one of them, measured rather than assumed. So the branch that matters most to a real user — being
 * asked, and saying no — is not reachable on the hardware this project has, and there is no
 * emulator and no system image installed on the build machine either.
 *
 * **Covered here:**
 * - the API boundary is at 33, and is not 32 and not 34;
 * - below it the app never asks and never suppresses, whatever a permission checker returns;
 * - above it a denial stops the post rather than throwing or posting into a void;
 * - the decision is a function of its inputs and of nothing else — no clock, no context, no state;
 * - the four reasons to stay quiet, one at a time and in combination;
 * - the check interval is the weather interval times 24, and is 24 hours.
 *
 * **Not covered here, and it must not be claimed:** that the permission is actually requested, that
 * the system dialog appears, that a user's answer reaches the switch, that the channel exists, that
 * anything is drawn, or that any of this behaves on an Android 13+ device. A green run of this class
 * is evidence about a decision, not about a phone.
 *
 * The same shape as `MoonPhaseControlTest` and `LakeContentsControlTest`: the rule is pure, so the
 * rule is asserted; everything Android owns is on the other side of [UpdateNotifier].
 */
class UpdateNotificationPolicyTest {

    // ------------------------------------------------------- the boundary, at every level that matters

    /**
     * The six SDK levels this app can meet, granted and denied.
     *
     * 26 is `minSdk`; 29 is the test device; **32 and 33 are the boundary, one on each side**; 34 is
     * the level after it, so a fencepost error that shifted the rule upward would show; 37 is
     * `targetSdk`. A table rather than six tests because the point is the *shape* of the answer
     * across the range, and a single failure names the level it happened at.
     */
    @Test
    fun `the runtime permission begins at 33 and not at 32 or 34`() {
        val expected = mapOf(
            26 to NotificationPermission.NOT_REQUIRED,
            29 to NotificationPermission.NOT_REQUIRED,
            32 to NotificationPermission.NOT_REQUIRED,
            33 to NotificationPermission.GRANTED,
            34 to NotificationPermission.GRANTED,
            37 to NotificationPermission.GRANTED,
        )
        for ((sdk, want) in expected) {
            assertEquals(
                "sdkInt=$sdk with the permission granted",
                want,
                UpdateNotificationPolicy.permissionFor(sdkInt = sdk, granted = true),
            )
        }
    }

    /**
     * The same six levels with the grant refused — which is where the boundary earns its keep.
     *
     * Below 33 the answer must still be [NotificationPermission.NOT_REQUIRED]: on those platforms a
     * permission checker returning "not granted" means the permission is not in the runtime set, not
     * that the user refused, and reading it as a refusal would kill this feature on every Android 8
     * to 12 device — including the only one this project can test on.
     */
    @Test
    fun `below 33 a false grant is not a refusal`() {
        val expected = mapOf(
            26 to NotificationPermission.NOT_REQUIRED,
            29 to NotificationPermission.NOT_REQUIRED,
            32 to NotificationPermission.NOT_REQUIRED,
            33 to NotificationPermission.DENIED,
            34 to NotificationPermission.DENIED,
            37 to NotificationPermission.DENIED,
        )
        for ((sdk, want) in expected) {
            assertEquals(
                "sdkInt=$sdk with the permission not granted",
                want,
                UpdateNotificationPolicy.permissionFor(sdkInt = sdk, granted = false),
            )
        }
    }

    /** The constant is the rule; a literal 33 anywhere else would be a second copy of it. */
    @Test
    fun `the boundary constant is 33`() {
        assertEquals(33, UpdateNotificationPolicy.RUNTIME_PERMISSION_SDK)
        assertEquals(
            NotificationPermission.NOT_REQUIRED,
            UpdateNotificationPolicy.permissionFor(UpdateNotificationPolicy.RUNTIME_PERMISSION_SDK - 1, granted = true),
        )
        assertEquals(
            NotificationPermission.GRANTED,
            UpdateNotificationPolicy.permissionFor(UpdateNotificationPolicy.RUNTIME_PERMISSION_SDK, granted = true),
        )
    }

    // ------------------------------------------------------------------ may post / must ask

    /**
     * Posting is allowed unless it was refused, and asking happens only when there is something to ask.
     *
     * The two must not be each other's negation: [NotificationPermission.NOT_REQUIRED] both posts
     * and does not ask, which is the whole of the API-29 case.
     */
    @Test
    fun `not required posts without asking, granted posts, denied does neither`() {
        assertTrue(UpdateNotificationPolicy.mayPost(NotificationPermission.NOT_REQUIRED))
        assertFalse(UpdateNotificationPolicy.mustAsk(NotificationPermission.NOT_REQUIRED))

        assertTrue(UpdateNotificationPolicy.mayPost(NotificationPermission.GRANTED))
        assertFalse(UpdateNotificationPolicy.mustAsk(NotificationPermission.GRANTED))

        assertFalse(UpdateNotificationPolicy.mayPost(NotificationPermission.DENIED))
        assertTrue(UpdateNotificationPolicy.mustAsk(NotificationPermission.DENIED))
    }

    /** Every value is decided, so a fourth one added later cannot fall through silently. */
    @Test
    fun `every permission value has an answer to both questions`() {
        for (value in NotificationPermission.entries) {
            // Asserting only that these do not throw and that the pair is not contradictory:
            // nothing may both be refused and be worth asking about *and* still be postable.
            assertFalse(
                "$value both posts and must be asked for",
                UpdateNotificationPolicy.mayPost(value) && UpdateNotificationPolicy.mustAsk(value),
            )
        }
    }

    // ------------------------------------------------------------------------ the snooze

    private val monthMillis = 30L * 24 * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    /** A snooze suppresses the release it was taken on, and only while it lasts. */
    @Test
    fun `a snooze hides its own version until it expires`() {
        assertTrue(UpdateNotificationPolicy.isSnoozed("v5.7", "v5.7", now + monthMillis, now))
        assertFalse(UpdateNotificationPolicy.isSnoozed("v5.7", "v5.7", now - 1, now))
    }

    /**
     * **The one that matters**: a month on v5.6 does not hide v5.7.
     *
     * A rejected release is rejected; the idea of being told about releases is not. This is the
     * property the notification had to inherit rather than route around, and it is keyed on the tag
     * for exactly that reason.
     */
    @Test
    fun `a snooze on one version does not hide the next one`() {
        assertFalse(UpdateNotificationPolicy.isSnoozed("v5.7", "v5.6", now + monthMillis, now))
    }

    /** Nothing snoozed is not a snooze, however far in the future the stamp happens to be. */
    @Test
    fun `no stored tag is not a snooze`() {
        assertFalse(UpdateNotificationPolicy.isSnoozed("v5.7", null, now + monthMillis, now))
    }

    // ------------------------------------------------------------------- the whole decision

    private fun shouldPost(
        enabled: Boolean = true,
        permission: NotificationPermission = NotificationPermission.NOT_REQUIRED,
        tag: String = "v5.7",
        notified: String? = null,
        snoozedTag: String? = null,
        until: Long = 0L,
    ) = UpdateNotificationPolicy.shouldPost(
        notificationsEnabled = enabled,
        permission = permission,
        tagName = tag,
        alreadyNotifiedTag = notified,
        snoozedTag = snoozedTag,
        snoozeUntilMillis = until,
        nowMillis = now,
    )

    /** With nothing in the way, a newly found release is posted. */
    @Test
    fun `a new release with nothing in the way is posted`() {
        assertTrue(shouldPost())
        assertTrue(shouldPost(permission = NotificationPermission.GRANTED))
    }

    /** Each of the four reasons to stay quiet, alone. */
    @Test
    fun `the switch off, a denial, an already-notified tag and a live snooze each stop it`() {
        assertFalse("the switch is off", shouldPost(enabled = false))
        assertFalse("notifications are denied", shouldPost(permission = NotificationPermission.DENIED))
        assertFalse("this version was already notified", shouldPost(notified = "v5.7"))
        assertFalse(
            "this version is snoozed",
            shouldPost(snoozedTag = "v5.7", until = now + monthMillis),
        )
    }

    /**
     * **One notification per version tag**, which is the approved behaviour and not an accident.
     *
     * A release already raised once and left alone is not news again tomorrow; the release *after*
     * it is, and passes immediately because the tag is different.
     */
    @Test
    fun `an already-notified version stays quiet while the next one does not`() {
        assertFalse(shouldPost(tag = "v5.7", notified = "v5.7"))
        assertTrue(shouldPost(tag = "v5.8", notified = "v5.7"))
    }

    /** An expired snooze stops suppressing, and a snooze on another version never did. */
    @Test
    fun `an expired snooze and a snooze on another version both allow the post`() {
        assertTrue(shouldPost(snoozedTag = "v5.7", until = now - 1))
        assertTrue(shouldPost(snoozedTag = "v5.6", until = now + monthMillis))
    }

    // ------------------------------------------------------------------------- the schedule

    /**
     * The interval is derived, so the derivation is what is asserted — **and so is the result**.
     *
     * The same two-part check `SNAPSHOT_MAX_AGE_MILLIS` carries: halving the weather interval halves
     * this automatically, and the second assertion then fails so that the number has to be decided
     * again rather than inherited.
     */
    @Test
    fun `the check interval is twenty-four weather intervals, and that is a day`() {
        assertEquals(24 * WEATHER_REFRESH_INTERVAL_MS, UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS)
        assertEquals(24L * 60 * 60 * 1000, UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS)
    }

    /**
     * Every unreachable outcome earns a retry; a real answer does not, and nor does "no check ran".
     *
     * Unlike the weather providers, GitHub's public endpoint has no "the service answered no" for
     * this app — no key, no quota — so all three reasons collapse to "nothing is known" and all
     * three are worth asking again about.
     */
    @Test
    fun `only an unreachable check is retried sooner`() {
        for (reason in UpdateCheckResult.Unreachable.Reason.entries) {
            assertTrue(
                "$reason should be retried",
                UpdateNotificationPolicy.isTransient(UpdateCheckResult.Unreachable(reason)),
            )
        }
        assertFalse(UpdateNotificationPolicy.isTransient(UpdateCheckResult.UpToDate))
        assertFalse("no check was made on this pass", UpdateNotificationPolicy.isTransient(null))
    }
}
