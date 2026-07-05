package info.meuse24.pdf_scanner.util

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import info.meuse24.pdf_scanner.domain.model.OcrResultStats
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Ein echter [com.google.android.gms.tasks.Task] (z. B. via `TaskCompletionSource`/
 * `Tasks.forResult`) dispatcht seine Listener standardmäßig über `TaskExecutors.MAIN_THREAD`,
 * was einen echten Android-Main-Looper erfordert. Im reinen JVM-Unit-Test-Stub-Jar dieses
 * Projekts (kein Robolectric) ist `Looper.getMainLooper()` ein No-Op-Stub — der Listener würde
 * dadurch nie feuern und die Coroutine würde ewig hängen. [ControllableTask] mockt `Task` direkt
 * und ruft den registrierten Erfolgs-Listener synchron/gesteuert selbst auf, ganz ohne
 * Looper-Abhängigkeit.
 */
private class ControllableTask<T> {
    private var successListener: OnSuccessListener<in T>? = null
    private var failureListener: OnFailureListener? = null

    @Suppress("UNCHECKED_CAST")
    val task: Task<T> = mock(Task::class.java) as Task<T>

    init {
        `when`(task.addOnSuccessListener(any())).thenAnswer { invocation ->
            successListener = invocation.getArgument(0)
            task
        }
        `when`(task.addOnFailureListener(any())).thenAnswer { invocation ->
            failureListener = invocation.getArgument(0)
            task
        }
    }

    fun complete(result: T) {
        successListener?.onSuccess(result)
    }

    fun completeExceptionally(exception: Exception) {
        failureListener?.onFailure(exception)
    }
}

/** Task, deren Erfolgs-Listener sofort beim Registrieren mit [result] aufgerufen wird. */
private fun <T> immediateSuccessTask(result: T): Task<T> {
    @Suppress("UNCHECKED_CAST")
    val task = mock(Task::class.java) as Task<T>
    `when`(task.addOnSuccessListener(any())).thenAnswer { invocation ->
        val listener = invocation.getArgument<OnSuccessListener<T>>(0)
        listener.onSuccess(result)
        task
    }
    return task
}

class MlKitTextRecognizerRunnerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `cancellation wartet auf echten ML-Kit-Abschluss bevor sie durchschlaegt`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val image = mock(InputImage::class.java)
        val controllable = ControllableTask<Text>()
        `when`(recognizer.process(image)).thenReturn(controllable.task)
        val runner = MlKitTextRecognizerRunner(mock(PdfPageInputImageLoader::class.java))

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.recognizeFullText(recognizer, image)
        }

        job.cancel()
        // Trotz Cancellation darf der Job noch nicht abgeschlossen sein - er muss auf den
        // tatsächlichen ML-Kit-Abschluss warten (NonCancellable-Wrapping in recognizeFullText).
        // Ohne den Fix würde suspendCancellableCoroutine hier sofort mit
        // CancellationException verlassen, während der ML-Kit-Task im Hintergrund weiterläuft -
        // der Aufrufer würde dann Bitmap/Recognizer freigeben, während ML Kit noch verarbeitet.
        assertFalse(job.isCompleted)

        controllable.complete(mock(Text::class.java))
        job.join()

        assertTrue(job.isCompleted)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `nach Cancellation wird Code nach recognizeFullText nicht mehr ausgefuehrt`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val image = mock(InputImage::class.java)
        val controllable = ControllableTask<Text>()
        `when`(recognizer.process(image)).thenReturn(controllable.task)
        val runner = MlKitTextRecognizerRunner(mock(PdfPageInputImageLoader::class.java))
        var reachedCodeAfterCall = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.recognizeFullText(recognizer, image)
            reachedCodeAfterCall = true
        }

        job.cancel()
        controllable.complete(mock(Text::class.java))
        job.join()

        assertTrue(job.isCancelled)
        // Ohne ensureActive() nach dem NonCancellable-Block wuerde dieser Code (stellvertretend
        // fuer onPage()/Mapping/naechste PDF-Seite) trotz Cancellation noch ausgefuehrt.
        assertFalse(reachedCodeAfterCall)
    }

    @Test
    fun `bei Cancellation und ML-Kit-Fehlschlag wird trotzdem CancellationException geworfen`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val image = mock(InputImage::class.java)
        val controllable = ControllableTask<Text>()
        `when`(recognizer.process(image)).thenReturn(controllable.task)
        val runner = MlKitTextRecognizerRunner(mock(PdfPageInputImageLoader::class.java))
        var caught: Throwable? = null

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                runner.recognizeFullText(recognizer, image)
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }

        job.cancel()
        // ML Kit scheitert (statt erfolgreich abzuschliessen) - ohne ensureActive() im finally
        // wuerde withContext(NonCancellable) diese RuntimeException direkt durchreichen, ohne
        // dass der Aufrufer erkennt, dass eigentlich abgebrochen wurde.
        controllable.completeExceptionally(RuntimeException("ML Kit boom"))
        job.join()

        assertTrue(job.isCancelled)
        assertTrue("erwartet CancellationException, war $caught", caught is CancellationException)
    }

    @Test
    fun `processPagesFullText reicht Seitenmetadaten und erkannten Text durch`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val firstImage = mock(InputImage::class.java)
        val secondImage = mock(InputImage::class.java)
        val firstText = mock(Text::class.java)
        val secondText = mock(Text::class.java)
        val firstTask = immediateSuccessTask(firstText)
        val secondTask = immediateSuccessTask(secondText)
        `when`(recognizer.process(firstImage)).thenReturn(firstTask)
        `when`(recognizer.process(secondImage)).thenReturn(secondTask)

        val loader = FakePdfPageInputImageLoader(listOf(firstImage, secondImage))
        val runner = MlKitTextRecognizerRunner(loader)
        val pdfFile = File("dummy.pdf")
        val calls = mutableListOf<Triple<Int, Int, Text>>()
        val dims = mutableListOf<Pair<Int, Int>>()

        runner.processPagesFullText(pdfFile, recognizer, renderScale = PDF_TABLE_RENDER_SCALE) { pageIndex, pageCount, w, h, text ->
            calls += Triple(pageIndex, pageCount, text)
            dims += w to h
        }

        assertEquals(listOf(Triple(0, 2, firstText), Triple(1, 2, secondText)), calls)
        assertEquals(listOf(100 to 200, 100 to 200), dims)
        assertEquals(pdfFile, loader.capturedFile)
        assertEquals(PDF_TABLE_RENDER_SCALE, loader.capturedScale)
    }

    @Test
    fun `processPages delegiert an processPagesFullText und liefert identische Stats`() = runTest(testDispatcher) {
        val recognizer = mock(TextRecognizer::class.java)
        val firstImage = mock(InputImage::class.java)
        val secondImage = mock(InputImage::class.java)
        val firstText = mock(Text::class.java).apply {
            `when`(text).thenReturn("Alpha")
            `when`(textBlocks).thenReturn(emptyList())
        }
        val secondText = mock(Text::class.java).apply {
            `when`(text).thenReturn("Beta")
            `when`(textBlocks).thenReturn(emptyList())
        }
        val firstTask = immediateSuccessTask(firstText)
        val secondTask = immediateSuccessTask(secondText)
        `when`(recognizer.process(firstImage)).thenReturn(firstTask)
        `when`(recognizer.process(secondImage)).thenReturn(secondTask)

        val loader = FakePdfPageInputImageLoader(listOf(firstImage, secondImage))
        val runner = MlKitTextRecognizerRunner(loader)
        val results = mutableListOf<Pair<String, OcrResultStats?>>()

        runner.processPages(File("dummy.pdf"), recognizer) { text, stats -> results += text to stats }

        val blankStats = OcrResultStats(0f, null, 0f)
        assertEquals(listOf("Alpha" to blankStats, "Beta" to blankStats), results)
        // Default-Scale des Volltextpfads bleibt unverändert (150 DPI), auch nach der
        // Umstellung auf Delegation.
        assertEquals(PDF_OCR_RENDER_SCALE, loader.capturedScale)
    }
}

private class FakePdfPageInputImageLoader(
    private val images: List<InputImage>
) : PdfPageInputImageLoader {
    var capturedFile: File? = null
        private set
    var capturedScale: Float? = null
        private set

    override suspend fun forEachPageImage(
        sourceFile: File,
        renderScale: Float,
        onPageImage: suspend (pageIndex: Int, pageCount: Int, bitmapWidth: Int, bitmapHeight: Int, image: InputImage) -> Unit
    ) {
        capturedFile = sourceFile
        capturedScale = renderScale
        images.forEachIndexed { index, image ->
            onPageImage(index, images.size, 100, 200, image)
        }
    }
}
