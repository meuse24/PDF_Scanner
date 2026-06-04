package info.meuse24.pdf_scanner.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.backup.BACKUP_FILE_EXTENSION
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePrepareResult
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreResult
import info.meuse24.pdf_scanner.domain.backup.BackupResult
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.usecase.ExportBackupUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreBackupUseCase
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the encrypted backup feature as a small state machine. The SAF launches are signaled back
 * to the UI through [BackupUiState.pendingExportFilename] / [BackupUiState.launchOpenDocument];
 * the UI returns the chosen content-URI token via [onExportLocationChosen] / [onRestoreLocationChosen].
 *
 * Export order matches the plan: options → passphrase (twice) → CreateDocument → write + probe.
 * Restore is two-phased: OpenDocument → passphrase → decrypt+stage → summary → confirm/cancel.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var pendingExportOptions: BackupExportOptions? = null
    private var pendingExportPassphrase: CharArray? = null
    private var pendingRestoreToken: String? = null
    private var preparedSession: BackupStagingResult? = null
    private var runningJob: Job? = null

    // region Export

    fun onCreateBackupClicked() {
        _uiState.update { it.copy(dialog = BackupDialog.ExportOptions(BackupExportOptions())) }
    }

    fun onExportOptionsConfirmed(options: BackupExportOptions) {
        pendingExportOptions = options
        _uiState.update { it.copy(dialog = BackupDialog.ExportPassphrase()) }
    }

    fun onExportPassphraseConfirmed(passphrase: String, repeated: String) {
        when {
            passphrase.length < MIN_PASSPHRASE_LENGTH ->
                _uiState.update { it.copy(dialog = BackupDialog.ExportPassphrase(R.string.backup_error_passphrase_too_short)) }
            passphrase != repeated ->
                _uiState.update { it.copy(dialog = BackupDialog.ExportPassphrase(R.string.backup_error_passphrase_mismatch)) }
            else -> {
                pendingExportPassphrase = passphrase.toCharArray()
                _uiState.update {
                    it.copy(dialog = null, pendingExportFilename = defaultBackupFilename())
                }
            }
        }
    }

    /** Called once the UI has launched CreateDocument so the request is not re-triggered. */
    fun onExportFilenameConsumed() {
        _uiState.update { it.copy(pendingExportFilename = null) }
    }

    fun onExportLocationCancelled() {
        clearPendingExport()
        _uiState.update { it.copy(dialog = null) }
    }

    fun onExportLocationChosen(locationToken: String) {
        val options = pendingExportOptions ?: return
        val passphrase = pendingExportPassphrase ?: return
        pendingExportPassphrase = null
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(operation = BackupOperationState(BackupOperationKind.EXPORT))
            }
            try {
                val result = exportBackup(locationToken, passphrase, options, ::onExportProgress)
                _uiState.update { it.copy(operation = null, message = result.toMessage()) }
            } catch (cancellation: CancellationException) {
                _uiState.update {
                    it.copy(operation = null, message = BackupMessage.Error(string(R.string.backup_cancelled)))
                }
                throw cancellation
            } finally {
                pendingExportOptions = null
            }
        }
    }

    private fun onExportProgress(progress: BackupProgress) {
        _uiState.update { state ->
            val operation = state.operation ?: return@update state
            state.copy(operation = operation.copy(detail = progress.toDetail()))
        }
    }

    // endregion

    // region Restore

    fun onRestoreBackupClicked() {
        _uiState.update { it.copy(launchOpenDocument = true) }
    }

    fun onOpenDocumentConsumed() {
        _uiState.update { it.copy(launchOpenDocument = false) }
    }

    fun onRestoreLocationCancelled() {
        pendingRestoreToken = null
    }

    fun onRestoreLocationChosen(locationToken: String) {
        pendingRestoreToken = locationToken
        _uiState.update { it.copy(dialog = BackupDialog.RestorePassphrase()) }
    }

    fun onRestorePassphraseConfirmed(passphrase: String) {
        if (passphrase.isEmpty()) {
            _uiState.update { it.copy(dialog = BackupDialog.RestorePassphrase(R.string.backup_error_passphrase_empty)) }
            return
        }
        val token = pendingRestoreToken ?: return
        val chars = passphrase.toCharArray()
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(dialog = null, operation = BackupOperationState(BackupOperationKind.RESTORE_PREPARE))
            }
            try {
                when (val result = restoreBackup.prepare(token, chars, ::onRestoreProgress)) {
                    is BackupRestorePrepareResult.Ready -> {
                        preparedSession = result.session
                        _uiState.update {
                            it.copy(operation = null, dialog = BackupDialog.RestoreSummary(result.preview))
                        }
                    }

                    is BackupRestorePrepareResult.Failure -> {
                        pendingRestoreToken = null
                        _uiState.update {
                            it.copy(operation = null, message = BackupMessage.Error(result.reason.toText()))
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                pendingRestoreToken = null
                _uiState.update {
                    it.copy(operation = null, message = BackupMessage.Error(string(R.string.backup_cancelled)))
                }
                throw cancellation
            }
        }
    }

    fun onRestoreConfirmed() {
        val session = preparedSession ?: return
        preparedSession = null
        pendingRestoreToken = null
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(dialog = null, operation = BackupOperationState(BackupOperationKind.RESTORE, cancellable = false))
            }
            val result = restoreBackup.confirm(session)
            _uiState.update { it.copy(operation = null, message = result.toMessage()) }
        }
    }

    fun onRestoreSummaryDismissed() {
        val session = preparedSession ?: return
        preparedSession = null
        pendingRestoreToken = null
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch { restoreBackup.cancel(session) }
    }

    private fun onRestoreProgress(progress: BackupProgress) {
        _uiState.update { state ->
            val operation = state.operation ?: return@update state
            state.copy(operation = operation.copy(detail = progress.toDetail()))
        }
    }

    // endregion

    fun onDialogDismissed() {
        clearPendingExport()
        pendingRestoreToken = null
        _uiState.update { it.copy(dialog = null) }
    }

    fun onCancelOperation() {
        runningJob?.cancel()
        runningJob = null
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        clearPendingExport()
        // A prepared-but-unconfirmed restore leaves decrypted files in the cache. viewModelScope is
        // already cancelled here, so clean up synchronously rather than via a coroutine.
        preparedSession?.stagingDir?.deleteRecursively()
        preparedSession = null
        super.onCleared()
    }

    private fun clearPendingExport() {
        pendingExportOptions = null
        pendingExportPassphrase?.let { Arrays.fill(it, ' ') }
        pendingExportPassphrase = null
    }

    private fun BackupResult.toMessage(): BackupMessage = when (this) {
        is BackupResult.Success -> BackupMessage.Success(
            string(R.string.backup_export_success, manifest?.documents?.size ?: 0)
        )

        is BackupResult.Failure -> {
            val reason = reason.toText()
            val text = if (leftoverFileMayRemain) {
                string(R.string.backup_export_error_leftover, reason)
            } else {
                reason
            }
            BackupMessage.Error(text)
        }
    }

    private fun BackupRestoreResult.toMessage(): BackupMessage = when (this) {
        is BackupRestoreResult.Success -> BackupMessage.Success(
            string(
                R.string.backup_restore_success,
                summary.documentsRestored,
                summary.foldersCreated,
                summary.foldersReused
            )
        )

        is BackupRestoreResult.Failure -> BackupMessage.Error(reason.toText())
    }

    private fun BackupFailure.toText(): String = string(
        when (this) {
            BackupFailure.WrongPassphrase -> R.string.backup_error_wrong_passphrase
            BackupFailure.CorruptArchive -> R.string.backup_error_corrupt
            BackupFailure.UnsupportedFormat -> R.string.backup_error_unsupported_format
            BackupFailure.UnsupportedFutureVersion -> R.string.backup_error_future_version
            BackupFailure.InvalidManifest -> R.string.backup_error_invalid_manifest
            BackupFailure.MissingDocumentFile -> R.string.backup_error_missing_document
            BackupFailure.Cancelled -> R.string.backup_cancelled
            is BackupFailure.InsufficientStorage -> R.string.backup_error_insufficient_storage
            is BackupFailure.Io -> R.string.backup_error_io
        }
    )

    private fun BackupProgress.toDetail(): String? = when (this) {
        is BackupProgress.Writing -> string(R.string.backup_progress_counter, current, total)
        is BackupProgress.Reading -> total?.let { string(R.string.backup_progress_counter, current, it) }
        is BackupProgress.Preparing -> null
        BackupProgress.Finished -> null
    }

    private fun defaultBackupFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "m24-pdf-scanner-backup-$timestamp.$BACKUP_FILE_EXTENSION"
    }

    private fun string(resId: Int): String = resourceProvider.getString(resId)

    private fun string(resId: Int, vararg args: Any): String = resourceProvider.getString(resId, *args)

    private companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
