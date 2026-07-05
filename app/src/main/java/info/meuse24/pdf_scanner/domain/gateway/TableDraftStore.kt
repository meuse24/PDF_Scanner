package info.meuse24.pdf_scanner.domain.gateway

import info.meuse24.pdf_scanner.domain.model.TableDraft

/**
 * Entwurfs-Persistenz für den Tabellenexport-Review gegen Process Death. Implementierungen
 * schreiben atomar in einen räumbaren Cache-Ordner; ein defekter oder veralteter Draft wird
 * beim Laden verworfen statt einen Fehler zu werfen.
 */
interface TableDraftStore {
    suspend fun saveDraft(draft: TableDraft)
    suspend fun loadDraft(scanId: Long): TableDraft?
    suspend fun deleteDraft(scanId: Long)

    /** Räumt Drafts auf, die älter als das Store-eigene Alterslimit sind (Standard: 24 h). */
    suspend fun deleteStaleDrafts()
}
