package com.ethran.notable.editor.canvas

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
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

    @After
    fun noScrubLeft() {
        CanvasEventBus.activeScrub.value?.let(CanvasEventBus::endScrub)
    }

    @Test
    fun aPreviewRequestKeepsTheScrubItWasMadeIn() = runBlocking {
        val scrubA = CanvasEventBus.startScrub()
        // Listening before the request: a request nobody collects is dropped.
        val sent = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { CanvasEventBus.previewPage.first() }
        }
        CanvasEventBus.requestPreview("page-1")
        CanvasEventBus.startScrub() // scrub B starts while A's request waits out the debounce
        val request = sent.await()
        assertEquals(PreviewRequest(scrubA, "page-1"), request)
        assertFalse("A's preview is dropped in B", CanvasEventBus.isCurrentScrub(request.scrub))
    }

    @Test
    fun aRequestOfTheScrubUnderWayIsShown() = runBlocking {
        CanvasEventBus.startScrub()
        val sent = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { CanvasEventBus.previewPage.first() }
        }
        CanvasEventBus.requestPreview("page-1")
        assertTrue(CanvasEventBus.isCurrentScrub(sent.await().scrub))
    }

    @Test
    fun noRequestWithoutAScrub() = runBlocking {
        CanvasEventBus.endScrub(CanvasEventBus.startScrub())
        val sent = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(100) { CanvasEventBus.previewPage.first() }
        }
        CanvasEventBus.requestPreview("page-1")
        assertNull(sent.await())
    }

    @Test
    fun everyScrubHasItsOwnId() {
        val first = CanvasEventBus.startScrub()
        CanvasEventBus.endScrub(first)
        val second = CanvasEventBus.startScrub()
        assertNotEquals(first, second)
        assertEquals(second, CanvasEventBus.activeScrub.value)
    }

    @Test
    fun aLateEndOfAnEarlierScrubLeavesTheCurrentOne() {
        val first = CanvasEventBus.startScrub()
        val second = CanvasEventBus.startScrub()
        CanvasEventBus.endScrub(first) // e.g. an abandoned gesture's cleanup, run late
        assertEquals(second, CanvasEventBus.activeScrub.value)
        CanvasEventBus.endScrub(second)
        assertNull(CanvasEventBus.activeScrub.value)
    }
}
