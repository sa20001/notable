package com.ethran.notable.editor.canvas

import androidx.annotation.MainThread
import com.ethran.notable.utils.checkMainThread
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * QuickNav scrubs and the page previews asked for in them. Each scrub has its own id, carried by
 * the work queued for it (a preview, its end), so that work cannot act on a later scrub.
 *
 * @param checkThread fails or logs when [start] runs off the main thread.
 */
class QuickNavScrubs(
    private val checkThread: () -> Unit = { checkMainThread("QuickNavScrubs.start") },
) {
    /** The id of the last scrub started; written by [start] only. */
    private var lastScrub = 0

    private val _active = MutableStateFlow<Int?>(null)

    /** The scrub under way, or null. */
    val active: StateFlow<Int?> = _active.asStateFlow()

    private val _previews = MutableSharedFlow<PreviewRequest>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Preview requests; each names the scrub it was made in. Only [requestPreview] adds them. The
     * newest wins: a request not yet taken is replaced by a later one.
     */
    val previews: SharedFlow<PreviewRequest> = _previews.asSharedFlow()

    /** Starts a new scrub and returns its id, for [end]. */
    @MainThread
    fun start(): Int {
        checkThread()
        lastScrub++
        _active.value = lastScrub
        return lastScrub
    }

    /**
     * Asks for a preview of [pageId] in the current scrub. The request keeps that scrub's id, so it
     * is dropped if it arrives after the scrub ended. Does nothing if no scrub is under way.
     */
    fun requestPreview(pageId: String) {
        active.value?.let { _previews.tryEmit(PreviewRequest(it, pageId)) }
    }

    /** Ends [scrub]; does nothing if it is not the scrub under way (already ended, or replaced). */
    fun end(scrub: Int) {
        _active.compareAndSet(scrub, null)
    }

    /** [scrub] is the scrub under way: its queued work may still act. */
    fun isCurrent(scrub: Int) = active.value == scrub
}

/** A QuickNav page preview asked for during [scrub]. */
data class PreviewRequest(val scrub: Int, val pageId: String)
