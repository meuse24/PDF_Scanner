package info.meuse24.pdf_scanner.ui.navigation

sealed class Screen(val route: String) {
    data object Ablage  : Screen("ablage")
    data object Help    : Screen("help")
    data object Info    : Screen("info")
    data object Privacy : Screen("privacy")
    data object Split   : Screen("split/{scanId}") {
        fun createRoute(scanId: Long) = "split/$scanId"
    }
    data object Reorder : Screen("reorder/{scanId}") {
        fun createRoute(scanId: Long) = "reorder/$scanId"
    }
    data object RotatePages : Screen("rotate-pages/{scanId}") {
        fun createRoute(scanId: Long) = "rotate-pages/$scanId"
    }
    data object DeletePages : Screen("delete-pages/{scanId}") {
        fun createRoute(scanId: Long) = "delete-pages/$scanId"
    }
    data object ExtractPages : Screen("extract-pages/{scanId}") {
        fun createRoute(scanId: Long) = "extract-pages/$scanId"
    }
    data object DuplicatePages : Screen("duplicate-pages/{scanId}") {
        fun createRoute(scanId: Long) = "duplicate-pages/$scanId"
    }
    data object PageNumbers : Screen("page-numbers/{scanId}") {
        fun createRoute(scanId: Long) = "page-numbers/$scanId"
    }
    data object TextWatermark : Screen("text-watermark/{scanId}") {
        fun createRoute(scanId: Long) = "text-watermark/$scanId"
    }
    data object CompressPdf : Screen("compress-pdf/{scanId}") {
        fun createRoute(scanId: Long) = "compress-pdf/$scanId"
    }
    data object ProtectPdf : Screen("protect-pdf/{scanId}") {
        fun createRoute(scanId: Long) = "protect-pdf/$scanId"
    }
    data object UnlockPdf : Screen("unlock-pdf/{scanId}") {
        fun createRoute(scanId: Long) = "unlock-pdf/$scanId"
    }
    data object Signature : Screen("signature/{scanId}") {
        fun createRoute(scanId: Long) = "signature/$scanId"
    }
    data object RemoveTextLayer : Screen("remove-text-layer/{scanId}") {
        fun createRoute(scanId: Long) = "remove-text-layer/$scanId"
    }
    data object RemovePassword : Screen("remove-password/{scanId}") {
        fun createRoute(scanId: Long) = "remove-password/$scanId"
    }
    data object RestrictUsage : Screen("restrict-usage/{scanId}") {
        fun createRoute(scanId: Long) = "restrict-usage/$scanId"
    }
    data object Annotate : Screen("annotate/{scanId}") {
        fun createRoute(scanId: Long) = "annotate/$scanId"
    }
    data object Grayscale : Screen("grayscale/{scanId}") {
        fun createRoute(scanId: Long) = "grayscale/$scanId"
    }
    data object PdfMetadata : Screen("pdf-metadata/{scanId}") {
        fun createRoute(scanId: Long) = "pdf-metadata/$scanId"
    }
}
