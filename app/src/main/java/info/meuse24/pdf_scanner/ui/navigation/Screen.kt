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
}
