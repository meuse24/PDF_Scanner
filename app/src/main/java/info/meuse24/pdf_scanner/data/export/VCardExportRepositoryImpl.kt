package info.meuse24.pdf_scanner.data.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.repository.VCardExportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VCardExportRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VCardExportRepository {
    override suspend fun export(filenameHint: String, vCardContent: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "vcards").apply { mkdirs() }
        val sanitized = filenameHint
            .trim()
            .ifBlank { "business-card" }
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_')
            .ifBlank { "business-card" }
        val target = File(directory, "$sanitized.vcf")
        target.writeText(vCardContent)
        target
    }
}
