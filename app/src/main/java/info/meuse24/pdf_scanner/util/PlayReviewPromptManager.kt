package info.meuse24.pdf_scanner.util

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayReviewPromptManager @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun recordAppLaunch(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val firstSeenAt = prefs.getLong(KEY_FIRST_SEEN_AT_MILLIS, 0L).takeIf { it > 0L } ?: nowMillis
            val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
            prefs.edit()
                .putLong(KEY_FIRST_SEEN_AT_MILLIS, firstSeenAt)
                .putInt(KEY_LAUNCH_COUNT, launchCount)
                .apply()
        }
    }

    fun recordSuccessfulDocumentActionAndCheckEligibility(
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val firstSeenAt = prefs.getLong(KEY_FIRST_SEEN_AT_MILLIS, 0L).takeIf { it > 0L } ?: nowMillis
        val actionCount = prefs.getInt(KEY_SUCCESSFUL_DOCUMENT_ACTION_COUNT, 0) + 1
        val snapshot = PlayReviewPromptSnapshot(
            firstSeenAtMillis = firstSeenAt,
            launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0),
            successfulDocumentActionCount = actionCount,
            lastReviewRequestAtMillis = prefs.getLong(KEY_LAST_REVIEW_REQUEST_AT_MILLIS, 0L),
            reviewRequestCount = prefs.getInt(KEY_REVIEW_REQUEST_COUNT, 0)
        )
        val eligible = PlayReviewPromptPolicy.isEligible(snapshot, nowMillis)

        prefs.edit()
            .putLong(KEY_FIRST_SEEN_AT_MILLIS, firstSeenAt)
            .putInt(KEY_SUCCESSFUL_DOCUMENT_ACTION_COUNT, actionCount)
            .apply {
                if (eligible) {
                    putLong(KEY_LAST_REVIEW_REQUEST_AT_MILLIS, nowMillis)
                    putInt(KEY_REVIEW_REQUEST_COUNT, snapshot.reviewRequestCount + 1)
                }
            }
            .apply()

        eligible
    }

    fun launchReviewFlow(activity: Activity, onComplete: () -> Unit) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow()
                .addOnCompleteListener { requestTask ->
                    if (!requestTask.isSuccessful) {
                        onComplete()
                        return@addOnCompleteListener
                    }

                    manager.launchReviewFlow(activity, requestTask.result)
                        .addOnCompleteListener { onComplete() }
                }
        } catch (_: Exception) {
            onComplete()
        }
    }

    private companion object {
        const val PREFS_NAME = "play_review_prompt"
        const val KEY_FIRST_SEEN_AT_MILLIS = "first_seen_at_millis"
        const val KEY_LAUNCH_COUNT = "launch_count"
        const val KEY_SUCCESSFUL_DOCUMENT_ACTION_COUNT = "successful_document_action_count"
        const val KEY_LAST_REVIEW_REQUEST_AT_MILLIS = "last_review_request_at_millis"
        const val KEY_REVIEW_REQUEST_COUNT = "review_request_count"
    }
}
