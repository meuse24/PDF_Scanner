package info.meuse24.pdf_scanner.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Ablage : Screen("ablage", "Ablage")
    data object Help   : Screen("help",   "Hilfe")
    data object Info   : Screen("info",   "Info")
}
