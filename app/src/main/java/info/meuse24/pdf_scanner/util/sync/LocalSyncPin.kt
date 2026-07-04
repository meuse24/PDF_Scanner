package info.meuse24.pdf_scanner.util.sync

import java.security.SecureRandom

private const val PIN_DIGITS = 4
private const val PIN_UPPER_BOUND = 10_000

/** 4-digit numeric PIN (see plan1.md: memorability wins over entropy, offset by [LoginRateLimiter]). */
fun generateLocalSyncPin(random: SecureRandom = SecureRandom()): String =
    random.nextInt(PIN_UPPER_BOUND).toString().padStart(PIN_DIGITS, '0')

fun String.escapeHtml(): String = buildString(length) {
    for (char in this@escapeHtml) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
