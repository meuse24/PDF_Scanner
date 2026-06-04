package info.meuse24.pdf_scanner.ui.backup

import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePreview

/** Immutable UI state for the encrypted backup feature, driven by [BackupViewModel]. */
data class BackupUiState(
    val dialog: BackupDialog? = null,
    val operation: BackupOperationState? = null,
    val message: BackupMessage? = null,
    /** Non-null asks the UI to launch CreateDocument with this default filename. */
    val pendingExportFilename: String? = null,
    /** True asks the UI to launch OpenDocument to pick a backup to restore. */
    val launchOpenDocument: Boolean = false
)

sealed interface BackupDialog {
    data class ExportOptions(val options: BackupExportOptions) : BackupDialog
    data class ExportPassphrase(val errorRes: Int? = null) : BackupDialog
    data class RestorePassphrase(val errorRes: Int? = null) : BackupDialog
    data class RestoreSummary(val preview: BackupRestorePreview) : BackupDialog
}

enum class BackupOperationKind { EXPORT, RESTORE_PREPARE, RESTORE }

/** Progress overlay descriptor. [detail] is an already-localized counter such as "12 / 30". */
data class BackupOperationState(
    val kind: BackupOperationKind,
    val detail: String? = null,
    val cancellable: Boolean = true
)

sealed interface BackupMessage {
    data class Success(val text: String) : BackupMessage
    data class Error(val text: String) : BackupMessage
}
