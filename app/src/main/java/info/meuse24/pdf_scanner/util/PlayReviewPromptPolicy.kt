package info.meuse24.pdf_scanner.util

import java.util.concurrent.TimeUnit

internal data class PlayReviewPromptSnapshot(
    val firstSeenAtMillis: Long,
    val launchCount: Int,
    val successfulDocumentActionCount: Int,
    val lastReviewRequestAtMillis: Long,
    val reviewRequestCount: Int
)

internal object PlayReviewPromptPolicy {
    const val MIN_LAUNCH_COUNT = 3
    const val MIN_SUCCESSFUL_DOCUMENT_ACTION_COUNT = 3
    const val MAX_REVIEW_REQUEST_COUNT = 3

    val MIN_APP_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(3)
    val REVIEW_REQUEST_COOLDOWN_MILLIS: Long = TimeUnit.DAYS.toMillis(120)

    fun isEligible(snapshot: PlayReviewPromptSnapshot, nowMillis: Long): Boolean {
        if (snapshot.firstSeenAtMillis <= 0L) return false
        if (nowMillis - snapshot.firstSeenAtMillis < MIN_APP_AGE_MILLIS) return false
        if (snapshot.launchCount < MIN_LAUNCH_COUNT) return false
        if (snapshot.successfulDocumentActionCount < MIN_SUCCESSFUL_DOCUMENT_ACTION_COUNT) return false
        if (snapshot.reviewRequestCount >= MAX_REVIEW_REQUEST_COUNT) return false
        if (
            snapshot.lastReviewRequestAtMillis > 0L &&
            nowMillis - snapshot.lastReviewRequestAtMillis < REVIEW_REQUEST_COOLDOWN_MILLIS
        ) {
            return false
        }
        return true
    }
}
