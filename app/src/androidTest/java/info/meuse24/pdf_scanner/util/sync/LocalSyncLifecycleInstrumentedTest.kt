package info.meuse24.pdf_scanner.util.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import info.meuse24.pdf_scanner.data.local.AppDatabase
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.common.findLocalIPv4Address
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.util.AndroidResourceProvider
import info.meuse24.pdf_scanner.util.AndroidStorageProvider
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lifecycle regressions for the CPU/runtime defects in docs/pcsync.md: the server must
 * release its socket, network callback and activity state on every stop, and must survive
 * concurrent stop requests (D4) arriving from overlapping network callbacks.
 *
 * Needs a real LAN address to bind to, so it is skipped when the device has no Wi-Fi.
 */
@RunWith(AndroidJUnit4::class)
class LocalSyncLifecycleInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageProvider = AndroidStorageProvider(context)
    private val resourceProvider = AndroidResourceProvider(context)
    private lateinit var database: AppDatabase
    private lateinit var server: KtorLocalSyncServer

    @Before
    fun setUp() {
        assumeNotNull(findLocalIPv4Address())
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ScanRepository(database.scanDao())
        server = KtorLocalSyncServer(
            documentRepository = repository,
            importFileUseCase = ImportFileUseCase(
                fileUtil = FileUtil(context, storageProvider, resourceProvider),
                pdfEditor = PdfEditor(),
                repository = repository,
                resourceProvider = resourceProvider
            ),
            storageProvider = storageProvider,
            resourceProvider = resourceProvider,
            context = context
        )
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            runBlocking { server.stop() }
        }
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun runningServerReportsAConsistentActivitySnapshot() = runBlocking {
        server.start()

        val snapshot = server.activitySnapshot()

        assertNotNull("a running server must expose a snapshot", snapshot)
        assertEquals(0, snapshot!!.activeRequests)
        assertTrue("runtime must be measured from start", snapshot.runtimeMillis >= 0)
        assertTrue("a fresh run is not idle yet", snapshot.idleMillis < 5_000)
    }

    /**
     * Regression for D1: a stopped server must report null so the watchdog policy can
     * recognise it and tear the foreground service down instead of lingering.
     */
    @Test
    fun stoppedServerReportsNoActivitySnapshot() = runBlocking {
        server.start()
        server.stop()

        assertNull(server.activitySnapshot())
        assertEquals(LocalSyncState.Stopped, server.state.value)
    }

    /** The socket and network callback must be released, or the second start would fail. */
    @Test
    fun serverCanBeRestartedRepeatedly() = runBlocking {
        repeat(3) {
            server.start()
            assertTrue(server.state.value is LocalSyncState.Running)
            server.stop()
            assertEquals(LocalSyncState.Stopped, server.state.value)
        }
    }

    /** Regression for D4: overlapping network callbacks used to race on an unsynchronised stop. */
    @Test
    fun concurrentStopsAreSafe() = runBlocking {
        server.start()

        listOf(
            async { server.stop() },
            async { server.stop() },
            async { server.stop() }
        ).awaitAll()

        assertEquals(LocalSyncState.Stopped, server.state.value)
        assertNull(server.activitySnapshot())
    }

    @Test
    fun stoppingAServerThatNeverStartedIsANoOp() = runBlocking {
        server.stop()

        assertEquals(LocalSyncState.Stopped, server.state.value)
        assertNull(server.activitySnapshot())
    }
}
