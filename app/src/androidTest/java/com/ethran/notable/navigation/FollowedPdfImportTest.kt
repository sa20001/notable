package com.ethran.notable.navigation

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ethran.notable.data.db.AppDatabase
import com.ethran.notable.data.db.BookRepository
import com.ethran.notable.data.db.ImageRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageDao
import com.ethran.notable.data.db.PageRepository
import com.ethran.notable.data.db.StrokeRepository
import com.ethran.notable.data.events.DefaultAppEventBus
import com.ethran.notable.io.ImportEngine
import com.ethran.notable.io.ThumbnailBackfillQueue
import com.ethran.notable.navigation.DeepLinkResolver.Target
import com.ethran.notable.testing.TestDatabaseFactory
import com.ethran.notable.testing.createAppRepositoryForTest
import com.ethran.notable.testing.waitForCondition
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Importing a PDF that the notebook follows, from links that may arrive while it is imported. */
@RunWith(AndroidJUnit4::class)
class FollowedPdfImportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.cacheDir, "followed-pdf-import-test")
    private val pdf = File(dir, "book.pdf")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var resolver: DeepLinkResolver

    /** Page writes wait for it; completed unless a test holds the import halfway. */
    @Volatile
    private var pageWrites = CompletableDeferred(Unit)

    /** The page write, counted from 1, that fails; 0 for none. */
    @Volatile
    private var failingWrite = 0
    private val writes = AtomicInteger()

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
        writePdf(pdf, PAGES)
        db = TestDatabaseFactory.createInMemory(context)
        val pageDao = object : PageDao by db.pageDao() {
            override suspend fun create(page: Page): Long {
                pageWrites.await()
                check(writes.incrementAndGet() != failingWrite) { "Write failed" }
                return db.pageDao().create(page)
            }
        }
        val appRepository = createAppRepositoryForTest(context, db, pageDao = pageDao)
        val importEngine = ImportEngine(
            context,
            PageRepository(pageDao),
            BookRepository(db.notebookDao(), db.pageDao()),
            StrokeRepository(db.strokeDao()),
            ImageRepository(db.ImageDao()),
            DefaultAppEventBus(),
        )
        val thumbnails = mockk<ThumbnailBackfillQueue>(relaxed = true)
        resolver = DeepLinkResolver(appRepository, importEngine, thumbnails, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        dir.deleteRecursively()
    }

    /**
     * Links that arrive while the file is imported, from the import dialog or from the receiver,
     * all open the one notebook, complete, each at its own page.
     */
    @Test(timeout = 30_000)
    fun linksDuringAnImport_openTheOneNotebook_complete() = runBlocking {
        pageWrites = CompletableDeferred()
        val first = async { resolver.import(link(2)) }
        // The notebook exists, still without its pages.
        assertTrue(waitForCondition(10_000) { db.notebookDao().getAll().isNotEmpty() })
        val second = async { resolver.import(link(4)) }
        val resolved = async { resolver.resolve(link(5)) }
        delay(200) // Both have asked by now.
        pageWrites.complete(Unit)
        val targets = listOf(first.await(), second.await(), resolved.await())

        val book = db.notebookDao().getAll().single()
        assertEquals(PAGES, book.pageIds.size)
        assertEquals(listOf(1, 3, 4).map { Target.Editor(book.pageIds[it], book.id) }, targets)
    }

    /** A failed import leaves nothing behind, so the next link asks again, and a retry imports. */
    @Test(timeout = 30_000)
    fun failedImport_leavesNoNotebook_andCanBeRetried() = runBlocking {
        failingWrite = 3
        assertTrue(resolver.import(link(1)) is Target.Failed)
        assertEquals(emptyList<Any>(), db.notebookDao().getAll())
        assertEquals(Target.AskToImport(link(1)), resolver.resolve(link(1)))

        failingWrite = 0
        val target = resolver.import(link(1))
        val book = db.notebookDao().getAll().single()
        assertEquals(PAGES, book.pageIds.size)
        assertEquals(Target.Editor(book.pageIds[0], book.id), target)
    }

    private fun link(page: Int) = DeepLink.Pdf(pdf.path, page)

    private fun writePdf(file: File, pages: Int) {
        val document = PdfDocument()
        try {
            repeat(pages) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 280, it + 1).create())
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private companion object {
        const val PAGES = 5
    }
}
