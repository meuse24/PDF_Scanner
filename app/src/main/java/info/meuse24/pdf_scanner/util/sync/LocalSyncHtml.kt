package info.meuse24.pdf_scanner.util.sync

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.model.Document

internal fun renderLoginPage(resourceProvider: ResourceProvider, error: String?): String {
    val errorHtml = error?.let { "<p style=\"color:#b00020;\">${it.escapeHtml()}</p>" }.orEmpty()
    return """
        <!DOCTYPE html>
        <html lang="${resourceProvider.htmlLangAttr()}">
        <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>${resourceProvider.getString(R.string.local_sync_web_login_title).escapeHtml()}</title>
        </head>
        <body style="font-family: sans-serif; max-width: 420px; margin: 48px auto; padding: 0 16px;">
        <h1>${resourceProvider.getString(R.string.local_sync_web_login_heading).escapeHtml()}</h1>
        <p>${resourceProvider.getString(R.string.local_sync_web_login_instructions).escapeHtml()}</p>
        $errorHtml
        <form method="post" action="/login">
        <input type="text" name="pin" inputmode="numeric" pattern="[0-9]{4}" maxlength="4" autofocus
         style="font-size:2rem; letter-spacing:0.4rem; width:100%; padding:8px; box-sizing:border-box;" />
        <button type="submit" style="margin-top:16px; font-size:1.1rem; padding:8px 16px;">${
        resourceProvider.getString(R.string.local_sync_web_login_button).escapeHtml()
    }</button>
        </form>
        </body>
        </html>
    """.trimIndent()
}

internal fun renderLockedOutPage(resourceProvider: ResourceProvider): String = """
    <!DOCTYPE html>
    <html lang="${resourceProvider.htmlLangAttr()}">
    <head><meta charset="utf-8" /><title>${
    resourceProvider.getString(R.string.local_sync_web_locked_out_title).escapeHtml()
}</title></head>
    <body style="font-family: sans-serif; max-width: 420px; margin: 48px auto; padding: 0 16px;">
    <h1>${resourceProvider.getString(R.string.local_sync_web_locked_out_heading).escapeHtml()}</h1>
    <p>${resourceProvider.getString(R.string.local_sync_web_locked_out_message).escapeHtml()}</p>
    </body>
    </html>
""".trimIndent()

internal fun renderDocumentsPage(
    resourceProvider: ResourceProvider,
    documents: List<Document>,
    message: String? = null
): String {
    val messageHtml = message?.let { "<p style=\"color:#1b6e1b;\">${it.escapeHtml()}</p>" }.orEmpty()
    val items = documents.joinToString("\n") { document ->
        val meta = resourceProvider.getString(
            R.string.local_sync_web_document_meta,
            document.pageCount,
            formatFileSize(document.fileSize)
        )
        """<li>
            <a href="/documents/${document.id}/download">${document.filename.escapeHtml()}.pdf</a>
            &mdash; ${meta.escapeHtml()}
        </li>"""
    }
    return """
        <!DOCTYPE html>
        <html lang="${resourceProvider.htmlLangAttr()}">
        <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>${resourceProvider.getString(R.string.local_sync_web_documents_title).escapeHtml()}</title>
        </head>
        <body style="font-family: sans-serif; max-width: 640px; margin: 32px auto; padding: 0 16px;">
        <h1>${resourceProvider.getString(R.string.local_sync_web_documents_heading).escapeHtml()}</h1>
        $messageHtml
        <ul>$items</ul>
        <h2>${resourceProvider.getString(R.string.local_sync_web_upload_heading).escapeHtml()}</h2>
        <p>${resourceProvider.getString(R.string.local_sync_web_upload_hint).escapeHtml()}</p>
        <form method="post" action="/documents/upload" enctype="multipart/form-data">
        <input type="file" name="file" accept="application/pdf" required />
        <button type="submit" style="margin-top:12px; font-size:1.05rem; padding:8px 16px;">${
        resourceProvider.getString(R.string.local_sync_web_upload_button).escapeHtml()
    }</button>
        </form>
        </body>
        </html>
    """.trimIndent()
}

internal fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
}

/** Best-effort BCP-47-ish lang attribute derived from the app's current locale string resource. */
private fun ResourceProvider.htmlLangAttr(): String =
    getString(R.string.local_sync_web_html_lang)
