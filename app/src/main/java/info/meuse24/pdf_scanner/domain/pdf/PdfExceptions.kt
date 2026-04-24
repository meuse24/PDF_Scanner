package info.meuse24.pdf_scanner.domain.pdf

import java.io.IOException

open class PdfWrongPasswordException(cause: Throwable? = null) : IOException("Falsches Passwort", cause)

open class PdfPasswordRequiredException(cause: Throwable? = null) :
    IOException("PDF ist mit Benutzerpasswort geschützt", cause)
