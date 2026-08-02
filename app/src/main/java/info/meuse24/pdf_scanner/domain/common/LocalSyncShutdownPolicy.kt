package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.LocalSyncActivity

sealed interface LocalSyncShutdownReason {
    /** App-Lock engaged; a background server would otherwise bypass that UI gate. */
    data object AppLocked : LocalSyncShutdownReason

    /** Server stopped itself (network loss, hard stop) while the service was still up. */
    data object ServerNotRunning : LocalSyncShutdownReason

    data object IdleTimeout : LocalSyncShutdownReason

    data object MaxRuntimeExceeded : LocalSyncShutdownReason

    /** Android 15+ reported the dataSync foreground-service budget as exhausted. */
    data object SystemForegroundServiceTimeout : LocalSyncShutdownReason
}

/**
 * Decides whether the PC-Sync run has to end. Purely functional: no clock, no framework,
 * no side effects — every time value is passed in.
 *
 * Order is deliberate: App-Lock and "server no longer running" win over any time
 * consideration, and the hard runtime ceiling is checked before in-flight transfers so a
 * long-running transfer cannot defeat it.
 *
 * @return the reason to shut down, or null to keep running.
 */
fun evaluateLocalSyncShutdown(
    appLocked: Boolean,
    activity: LocalSyncActivity?,
    idleTimeoutMillis: Long,
    maxRuntimeMillis: Long
): LocalSyncShutdownReason? = when {
    appLocked -> LocalSyncShutdownReason.AppLocked
    activity == null -> LocalSyncShutdownReason.ServerNotRunning
    activity.runtimeMillis >= maxRuntimeMillis -> LocalSyncShutdownReason.MaxRuntimeExceeded
    activity.activeRequests > 0 -> null
    activity.idleMillis >= idleTimeoutMillis -> LocalSyncShutdownReason.IdleTimeout
    else -> null
}
