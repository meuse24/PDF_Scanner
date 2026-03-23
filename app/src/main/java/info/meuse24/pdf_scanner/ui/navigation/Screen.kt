package info.meuse24.pdf_scanner.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Ablage  : Screen("ablage",   "Ablage")
    data object Help    : Screen("help",     "Hilfe")
    data object Info    : Screen("info",     "Info")
    data object Privacy : Screen("privacy",  "Datenschutz")
    data object Split   : Screen("split/{scanId}", "Split") {
        fun createRoute(scanId: Long) = "split/$scanId"
    }
    data object Reorder : Screen("reorder/{scanId}", "Reorder") {
        fun createRoute(scanId: Long) = "reorder/$scanId"
    }
}
