package info.meuse24.pdf_scanner.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface StorageProvider {
    fun scansDir(): File
    fun tempDir(): File
}

class AndroidStorageProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StorageProvider {
    override fun scansDir(): File = File(context.filesDir, "scans").apply { mkdirs() }

    override fun tempDir(): File = File(context.cacheDir, "temp").apply { mkdirs() }
}
