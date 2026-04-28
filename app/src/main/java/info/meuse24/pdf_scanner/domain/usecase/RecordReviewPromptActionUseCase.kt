package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.ReviewPromptPolicy
import javax.inject.Inject

class RecordReviewPromptActionUseCase @Inject constructor(
    private val reviewPromptPolicy: ReviewPromptPolicy
) {
    operator fun invoke(): Boolean =
        reviewPromptPolicy.recordSuccessfulDocumentActionAndCheckEligibility()
}
