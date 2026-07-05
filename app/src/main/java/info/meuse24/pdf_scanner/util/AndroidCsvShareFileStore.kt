package info.meuse24.pdf_scanner.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.gateway.CsvShareFileStore
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

private const val CSV_SHARE_SUBDIR = "csv"
private const val MAX_SHARE_FILE_AGE_MILLIS = 24L * 60 * 60 * 1000

class AndroidCsvShareFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CsvShareFileStore {
    override fun writeShareFile(displayName: String, writer: (OutputStream) -> Unit): File {
        val dir = File(context.cacheDir, CSV_SHARE_SUBDIR).apply { mkdirs() }
        cleanupOldFiles(dir)

        val sanitized = displayName
            .trim()
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_')
            .ifBlank { "table" }
        val target = File(dir, sanitized)
        // Eindeutiger Temp-Name (statt eines fixen "<sanitized>.tmp"), damit sich parallele
        // Exporte desselben Dateinamens nicht gegenseitig die Temp-Datei überschreiben.
        val temp = File.createTempFile("$sanitized-", ".tmp", dir)
        try {
            temp.outputStream().use { output -> writer(output) }
            // Files.move mit ATOMIC_MOVE + REPLACE_EXISTING ersetzt die Zieldatei in einem
            // einzigen Dateisystem-Aufruf (kein delete() gefolgt von renameTo() — dazwischen
            // gäbe es ein Zeitfenster ganz ohne Zieldatei).
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (throwable: Throwable) {
            temp.delete()
            throw throwable
        }
        return target
    }

    private fun cleanupOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_SHARE_FILE_AGE_MILLIS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
