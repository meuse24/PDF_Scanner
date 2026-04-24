package info.meuse24.pdf_scanner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReviewPromptPolicyTest {
    @Test
    fun `not eligible before minimum app age`() {
        val now = 1_000_000L
        val snapshot = eligibleSnapshot(
            firstSeenAtMillis = now - PlayReviewPromptPolicy.MIN_APP_AGE_MILLIS + 1
        )

        assertFalse(PlayReviewPromptPolicy.isEligible(snapshot, now))
    }

    @Test
    fun `eligible after enough age launches and successful document actions`() {
        val now = 1_000_000_000L
        val snapshot = eligibleSnapshot(
            firstSeenAtMillis = now - PlayReviewPromptPolicy.MIN_APP_AGE_MILLIS
        )

        assertTrue(PlayReviewPromptPolicy.isEligible(snapshot, now))
    }

    @Test
    fun `not eligible while cooldown is active`() {
        val now = 1_000_000_000L
        val snapshot = eligibleSnapshot(
            firstSeenAtMillis = now - PlayReviewPromptPolicy.MIN_APP_AGE_MILLIS,
            lastReviewRequestAtMillis = now - PlayReviewPromptPolicy.REVIEW_REQUEST_COOLDOWN_MILLIS + 1,
            reviewRequestCount = 1
        )

        assertFalse(PlayReviewPromptPolicy.isEligible(snapshot, now))
    }

    @Test
    fun `not eligible after maximum request count`() {
        val now = 1_000_000_000L
        val snapshot = eligibleSnapshot(
            firstSeenAtMillis = now - PlayReviewPromptPolicy.MIN_APP_AGE_MILLIS,
            reviewRequestCount = PlayReviewPromptPolicy.MAX_REVIEW_REQUEST_COUNT
        )

        assertFalse(PlayReviewPromptPolicy.isEligible(snapshot, now))
    }

    private fun eligibleSnapshot(
        firstSeenAtMillis: Long,
        launchCount: Int = PlayReviewPromptPolicy.MIN_LAUNCH_COUNT,
        successfulDocumentActionCount: Int = PlayReviewPromptPolicy.MIN_SUCCESSFUL_DOCUMENT_ACTION_COUNT,
        lastReviewRequestAtMillis: Long = 0L,
        reviewRequestCount: Int = 0
    ) = PlayReviewPromptSnapshot(
        firstSeenAtMillis = firstSeenAtMillis,
        launchCount = launchCount,
        successfulDocumentActionCount = successfulDocumentActionCount,
        lastReviewRequestAtMillis = lastReviewRequestAtMillis,
        reviewRequestCount = reviewRequestCount
    )
}
