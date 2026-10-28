package com.paperscrape.livewallpaper.update

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The phone stopping one update job cancels that job's run, and no other.**
 *
 * The periodic job (1515) and the one run (1516) can run together on the one [UpdateCheckJobService]. With one `Job` for
 * the service, the last one started -- 1516 on the network, 1515 started and ended at once on the check's lock -- the
 * phone stopping 1516 when the network goes would cancel 1515's run, already over, while 1516's would go on without its
 * wakelock, to write the preferences and perhaps post the notification. [RunsById] keeps each job's run under its id;
 * `ServiceWiringTest` holds the service to it on its source, where a single `running?.cancel()` fails.
 */
class UpdateJobStopTest {

    private val periodic = UpdateCheckSchedule.PERIODIC_JOB_ID
    private val oneRun = UpdateCheckSchedule.ONE_RUN_JOB_ID

    @Test
    fun `stopping the one run cancels the one run, not the periodic run started after it`() {
        val runs = RunsById()
        val first = Job().also { runs.started(oneRun, it) }
        val second = Job().also { runs.started(periodic, it) }
        runs.stop(oneRun)
        assertTrue("the run the phone stopped", first.isCancelled)
        assertFalse("the other job's run", second.isCancelled)
        assertEquals(setOf(periodic), runs.inProgress())
    }

    @Test
    fun `a run that ended is forgotten, and stopping its job then cancels nothing`() {
        val runs = RunsById()
        val ended = Job().also { runs.started(periodic, it) }
        ended.complete()
        runs.ended(periodic, ended)
        val other = Job().also { runs.started(oneRun, it) }
        runs.stop(periodic)
        assertFalse(other.isCancelled)
        assertEquals(setOf(oneRun), runs.inProgress())
    }

    @Test
    fun `the end of an older run does not forget a newer run of the same job`() {
        val runs = RunsById()
        val older = Job().also { runs.started(periodic, it) }
        val newer = Job().also { runs.started(periodic, it) }
        runs.ended(periodic, older)
        runs.stop(periodic)
        assertTrue("the newer run is still the job's, and is stopped", newer.isCancelled)
    }
}
