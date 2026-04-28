package info.meuse24.pdf_scanner.domain.gateway

interface ReviewPromptPolicy {
    fun recordSuccessfulDocumentActionAndCheckEligibility(): Boolean
}
