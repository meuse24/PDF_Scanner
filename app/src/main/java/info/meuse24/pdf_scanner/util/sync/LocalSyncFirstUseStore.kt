package info.meuse24.pdf_scanner.util.sync

import android.content.Context

private const val PREFS_NAME = "local_sync_prefs"
private const val KEY_NOTICE_ACKNOWLEDGED = "first_use_notice_acknowledged"

/**
 * Whether the user has already confirmed the local-network exposure notice once.
 * Scoped to this single feature on purpose (see plan1.md) instead of going through
 * the shared AppSettings/AppSettingsRepository plumbing for one narrow boolean.
 */
class LocalSyncFirstUseStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasAcknowledgedNotice(): Boolean = prefs.getBoolean(KEY_NOTICE_ACKNOWLEDGED, false)

    fun setNoticeAcknowledged() {
        prefs.edit().putBoolean(KEY_NOTICE_ACKNOWLEDGED, true).apply()
    }
}
