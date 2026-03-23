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
}
