package info.meuse24.pdf_scanner.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.TableDraftStore
import info.meuse24.pdf_scanner.domain.model.TableDraft
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TABLE_DRAFTS_SUBDIR = "table_drafts"
private const val MAX_DRAFT_AGE_MILLIS = 24L * 60 * 60 * 1000

class FileTableDraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : TableDraftStore {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val dir: File get() = File(context.cacheDir, TABLE_DRAFTS_SUBDIR).apply { mkdirs() }

    override suspend fun saveDraft(draft: TableDraft) {
        withContext(dispatcherProvider.io) {
            val target = File(dir, "${draft.scanId}.json")
            // Mindestens 3 Zeichen Praefix noetig (File.createTempFile-Vorgabe) - bei kurzen
            // scanId-Werten (z. B. "1") reicht "<scanId>-" allein nicht aus.
            val temp = File.createTempFile("draft-${draft.scanId}-", ".tmp", dir)
            try {
                temp.writeText(json.encodeToString(draft))
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (throwable: Throwable) {
                temp.delete()
                throw throwable
            }
        }
    }

    override suspend fun loadDraft(scanId: Long): TableDraft? {
        return withContext(dispatcherProvider.io) {
            val file = File(dir, "$scanId.json")
            if (!file.exists()) return@withContext null
            try {
                json.decodeFromString<TableDraft>(file.readText())
            } catch (e: SerializationException) {
                // Defekter/veralteter Draft wird verworfen statt die App abstürzen zu lassen.
                file.delete()
                null
            } catch (e: IllegalArgumentException) {
                file.delete()
                null
            } catch (e: IOException) {
                // Komfort-Cache: ein Lesefehler (z. B. Datei gerade parallel entfernt) ist kein
                // Absturzgrund; die Datei wird bewusst nicht geloescht, da das Problem transient
                // sein kann und nicht zwingend am Inhalt liegt.
                null
            }
        }
    }

    override suspend fun deleteDraft(scanId: Long) {
        withContext(dispatcherProvider.io) {
            File(dir, "$scanId.json").delete()
            Unit
        }
    }

    override suspend fun deleteStaleDrafts() {
        withContext(dispatcherProvider.io) {
            val cutoff = System.currentTimeMillis() - MAX_DRAFT_AGE_MILLIS
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
            Unit
        }
    }
}
