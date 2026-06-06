package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.gateway.DocxDocument
import info.meuse24.pdf_scanner.domain.gateway.DocxPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxBuilderImplTest {
    @Test
    fun `writes required openxml parts`() {
        val entries = buildDocxEntries()

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("docProps/core.xml"))
        assertTrue(entries.containsKey("word/document.xml"))
    }

    @Test
    fun `document xml contains escaped text heading and rtl properties`() {
        val entries = buildDocxEntries(
            DocxDocument(
                title = "Title & More",
                pages = listOf(
                    DocxPage(
                        heading = "Heading <One>",
                        paragraphs = listOf("A & B", "هذا نص")
                    )
                )
            )
        )
        val documentXml = entries.getValue("word/document.xml")

        assertTrue(documentXml.contains("Heading &lt;One&gt;"))
        assertTrue(documentXml.contains("A &amp; B"))
        assertTrue(documentXml.contains("<w:bidi/>"))
        assertTrue(documentXml.contains("<w:rtl/>"))
    }

    @Test
    fun `document xml removes illegal control characters`() {
        val entries = buildDocxEntries(
            DocxDocument(
                title = "Scan\u0000",
                pages = listOf(DocxPage(heading = null, paragraphs = listOf("Before\u000CAfter")))
            )
        )
        val documentXml = entries.getValue("word/document.xml")
        val coreXml = entries.getValue("docProps/core.xml")

        assertTrue(documentXml.contains("BeforeAfter"))
        assertTrue('\u000C' !in documentXml)
        assertTrue('\u0000' !in coreXml)
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml.toByteArray()))
    }

    @Test
    fun `xml parts are parseable`() {
        val entries = buildDocxEntries()
        val factory = DocumentBuilderFactory.newInstance()

        assertNotNull(factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("[Content_Types].xml").toByteArray())))
        assertNotNull(factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("_rels/.rels").toByteArray())))
        assertNotNull(factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("docProps/core.xml").toByteArray())))
        assertNotNull(factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("word/document.xml").toByteArray())))
    }

    private fun buildDocxEntries(
        doc: DocxDocument = DocxDocument(
            title = "Scan",
            pages = listOf(DocxPage(heading = "Page 1", paragraphs = listOf("Hello world")))
        )
    ): Map<String, String> {
        val output = ByteArrayOutputStream()
        DocxBuilderImpl().writeDocx(doc, output)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}
