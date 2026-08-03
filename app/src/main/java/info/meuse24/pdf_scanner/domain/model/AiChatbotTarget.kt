package info.meuse24.pdf_scanner.domain.model

data class AiChatbotTarget(val name: String, val url: String)

val defaultAiChatbotTargets = listOf(
    AiChatbotTarget("Claude", "https://claude.ai/"),
    AiChatbotTarget("ChatGPT", "https://chatgpt.com/"),
    AiChatbotTarget("Gemini", "https://gemini.google.com/")
)
