package info.meuse24.pdf_scanner.testutil

import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class FakeResourceProvider(
    private val strings: Map<Int, String> = emptyMap(),
    private val fallback: String = "stub"
) : ResourceProvider {
    override fun getString(resId: Int): String = strings[resId] ?: fallback

    override fun getString(resId: Int, vararg args: Any): String {
        val template = strings[resId] ?: fallback
        return String.format(template, *args)
    }
}

class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class TestStorageProvider(
    private val rootDir: File
) : StorageProvider {
    override fun scansDir(): File = File(rootDir, "scans").apply { mkdirs() }

    override fun tempDir(): File = File(rootDir, "temp").apply { mkdirs() }
}
