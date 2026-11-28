package com.ethran.notable.editor.canvas

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Work queued for a QuickNav scrub (a preview request through its debounce, the scrub's end)
 * belongs to that scrub, and cannot act on the next one.
 */
class QuickNavScrubTest {
    private var threadChecks = 0
    private val scrubs = QuickNavScrubs(checkThread = { threadChecks++ })

    /** Listens for the next preview request; a request sent with nobody listening is dropped. */
    private fun CoroutineScope.nextPreview(
        timeoutMillis: Long = 1000,
    ): Deferred<PreviewRequest?> = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(timeoutMillis) { scrubs.previews.first() }
    }

    @Test
    fun aPreviewRequestKeepsTheScrubItWasMadeIn() = runBlocking {
        val scrubA = scrubs.start()
        val sent = nextPreview()
        scrubs.requestPreview("page-1")
        scrubs.start() // scrub B starts while A's request waits out the debounce
        val request = sent.await()
        assertEquals(PreviewRequest(scrubA, "page-1"), request)
        assertFalse("A's preview is dropped in B", scrubs.isCurrent(scrubA))
    }

    @Test
    fun aRequestOfTheScrubUnderWayIsShown() = runBlocking {
        val scrub = scrubs.start()
        val sent = nextPreview()
        scrubs.requestPreview("page-1")
        assertEquals(PreviewRequest(scrub, "page-1"), sent.await())
        assertTrue(scrubs.isCurrent(scrub))
    }

    @Test
    fun noRequestWithoutAScrub() = runBlocking {
        scrubs.end(scrubs.start())
        val sent = nextPreview(timeoutMillis = 100)
        scrubs.requestPreview("page-1")
        assertNull(sent.await())
    }

    @Test
    fun everyScrubHasItsOwnId() {
        val first = scrubs.start()
        scrubs.end(first)
        val second = scrubs.start()
        assertNotEquals(first, second)
        assertEquals(second, scrubs.active.value)
    }

    @Test
    fun aLateEndOfAnEarlierScrubLeavesTheCurrentOne() {
        val first = scrubs.start()
        val second = scrubs.start()
        scrubs.end(first) // e.g. an abandoned gesture's cleanup, run late
        assertEquals(second, scrubs.active.value)
        scrubs.end(second)
        assertNull(scrubs.active.value)
    }

    @Test
    fun startingAScrubChecksTheThread() {
        scrubs.start()
        assertEquals(1, threadChecks)
    }
}
