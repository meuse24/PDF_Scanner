package info.meuse24.pdf_scanner.util

import info.meuse24.pdf_scanner.domain.gateway.DocxBuilder
import info.meuse24.pdf_scanner.domain.gateway.DocxDocument
import info.meuse24.pdf_scanner.domain.gateway.DocxPage
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocxBuilderImpl @Inject constructor() : DocxBuilder {
    override fun writeDocx(doc: DocxDocument, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypesXml())
            zip.writeEntry("_rels/.rels", packageRelationshipsXml())
            zip.writeEntry("docProps/core.xml", corePropertiesXml(doc))
            zip.writeEntry("word/document.xml", documentXml(doc))
        }
    }
}

private fun ZipOutputStream.writeEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun contentTypesXml(): String =
    xmlHeader() + """
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>
""".trimIndent()

private fun packageRelationshipsXml(): String =
    xmlHeader() + """
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>
""".trimIndent()

private fun corePropertiesXml(doc: DocxDocument): String {
    val title = doc.title.orEmpty().xmlEscaped()
    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    return xmlHeader() + """
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>$title</dc:title>
  <dc:creator>M24 PDF-Scanner</dc:creator>
  <cp:lastModifiedBy>M24 PDF-Scanner</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>
""".trimIndent()
}

private fun documentXml(doc: DocxDocument): String = buildString {
    append(xmlHeader())
    append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
    append("<w:body>")
    doc.pages.forEachIndexed { pageIndex, page ->
        page.heading?.takeIf { it.isNotBlank() }?.let { heading ->
            appendParagraph(heading, heading = true)
        }
        page.paragraphs
            .filter { it.isNotBlank() }
            .forEach { paragraph -> appendParagraph(paragraph, heading = false) }
        if (pageIndex < doc.pages.lastIndex) appendPageBreak()
    }
    append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
    append("</w:body></w:document>")
}

private fun StringBuilder.appendParagraph(text: String, heading: Boolean) {
    val rtl = text.containsRtlText()
    append("<w:p>")
    if (rtl) append("<w:pPr><w:bidi/></w:pPr>")
    append("<w:r>")
    if (heading || rtl) {
        append("<w:rPr>")
        if (heading) append("""<w:b/><w:sz w:val="28"/>""")
        if (rtl) append("<w:rtl/>")
        append("</w:rPr>")
    }
    append("""<w:t xml:space="preserve">""")
    append(text.xmlEscaped())
    append("</w:t></w:r></w:p>")
}

private fun StringBuilder.appendPageBreak() {
    append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
}

private fun String.xmlEscaped(): String = buildString(length) {
    this@xmlEscaped.codePoints().forEach { codePoint ->
        if (!codePoint.isValidXmlCodePoint()) return@forEach
        when (codePoint) {
            '&'.code -> append("&amp;")
            '<'.code -> append("&lt;")
            '>'.code -> append("&gt;")
            '"'.code -> append("&quot;")
            '\''.code -> append("&apos;")
            else -> append(String(Character.toChars(codePoint)))
        }
    }
}

private fun Int.isValidXmlCodePoint(): Boolean =
    this == 0x9 ||
        this == 0xA ||
        this == 0xD ||
        this in 0x20..0xD7FF ||
        this in 0xE000..0xFFFD ||
        this in 0x10000..0x10FFFF

private fun String.containsRtlText(): Boolean =
    any { char ->
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> true
            else -> false
        }
    }

private fun xmlHeader(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
