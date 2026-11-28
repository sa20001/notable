package com.ethran.notable.testing

import android.content.Context
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.model.SheetSize
import com.ethran.notable.editor.PageLeases
import com.ethran.notable.editor.PageView
import com.ethran.notable.ui.SnackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/** An A6-proportioned sheet. */
internal val A6_SHEET = SheetSize(600, 849)

/** An A5-proportioned sheet. */
internal val A5_SHEET = SheetSize(840, 1188)

/**
 * [PageView]s over one [PageDataTestHarness], as the editor builds them, for pages with or without a
 * sheet. [close] closes every view and the data layer.
 */
internal class SheetPageViews(private val context: Context) : AutoCloseable {
    val data = PageDataTestHarness(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val leases = PageLeases(data.pageDataManager)
    private val views = mutableListOf<PageView>()

    /** A page with [sheet] (null: an infinite page), in no notebook; returns its id. */
    suspend fun seedPage(sheet: SheetSize?): String = Page(sheet = sheet).also { data.db.pageDao().create(it) }.id

    /** A view [width] x [height] of [pageId], returned once it shows the page loaded. */
    fun open(pageId: String, width: Int, height: Int): PageView {
        val view = PageView(
            context = context,
            coroutineScope = scope,
            pageDataManager = data.pageDataManager,
            pageLeases = leases,
            appRepository = data.appRepository,
            initialPageId = pageId,
            viewWidth = width,
            viewHeight = height,
            snackManager = SnackState(),
            onPageShown = {},
        )
        views += view
        awaitShown(view, pageId)
        return view
    }

    /** Pages [view] to [pageId] and waits until it shows it loaded. */
    fun show(view: PageView, pageId: String) {
        view.changePage(pageId)
        awaitShown(view, pageId)
    }

    private fun awaitShown(view: PageView, pageId: String) = check(
        waitForCondition(10_000) {
            view.currentPageId == pageId && view.isTransformationAllowed && view.isPageDataLoaded
        },
    ) { "The view never showed page $pageId" }

    override fun close() {
        for (view in views) view.disposeOldPage()
        scope.cancel()
        data.close()
    }
}

/** Runs [step] on the main thread, where the editor runs scroll, zoom and resize steps. */
internal suspend fun onMain(step: suspend () -> Unit) = withContext(Dispatchers.Main) { step() }
