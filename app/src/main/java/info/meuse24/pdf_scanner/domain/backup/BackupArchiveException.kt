package info.meuse24.pdf_scanner.domain.backup

import java.io.IOException

/**
 * Carries a typed [BackupFailure] across the backup gateways (codec, staging reader, restore
 * writer). It lives in the domain layer because the orchestrating use cases catch it to map the
 * failure into [BackupResult]/[BackupRestoreResult]; implementations in the data layer throw it.
 */
class BackupArchiveException(
    val failure: BackupFailure,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
