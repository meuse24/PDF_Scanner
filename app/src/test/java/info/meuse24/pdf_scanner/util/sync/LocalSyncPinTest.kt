package info.meuse24.pdf_scanner.util.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class LocalSyncPinTest {

    @Test
    fun `pin is always four digits, zero-padded`() {
        val random = SecureRandom()
        repeat(200) {
            val pin = generateLocalSyncPin(random)
            assertEquals(4, pin.length)
            assertTrue(pin.all(Char::isDigit))
        }
    }

    @Test
    fun `escapeHtml neutralizes markup-relevant characters`() {
        assertEquals(
            "Rechnung &lt;script&gt;&quot;test&quot;&#39;s&lt;/script&gt;",
            "Rechnung <script>\"test\"'s</script>".escapeHtml()
        )
    }

    @Test
    fun `escapeHtml escapes ampersands first so entities are not double-escaped`() {
        assertEquals("A &amp; B", "A & B".escapeHtml())
    }

    @Test
    fun `escapeHtml leaves plain text untouched`() {
        assertEquals("Rechnung_2026.pdf", "Rechnung_2026.pdf".escapeHtml())
    }
}
