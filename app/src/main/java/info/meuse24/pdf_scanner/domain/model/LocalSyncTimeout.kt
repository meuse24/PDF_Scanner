package info.meuse24.pdf_scanner.domain.model

/**
 * Single source of truth for how long a PC-Sync run may stay alive.
 *
 * Deliberately offers no "never" option: an unbounded run was the defect this
 * type exists to prevent (see docs/pcsync.md). The setting can only shorten the
 * protection, never disable it.
 */
object LocalSyncTimeout {
    const val DEFAULT_MINUTES = 20

    /**
     * Hard ceiling for a single server run, intentionally NOT configurable.
     * Second safeguard independent of the idle timeout: even if activity is kept
     * fresh indefinitely, the run ends here.
     */
    const val MAX_SESSION_RUNTIME_MINUTES = 120

    val SELECTABLE_MINUTES = listOf(5, 10, 20, 30, 60)

    /**
     * Persistence must not establish in-between values (e.g. 17 minutes): only the
     * values offered in the UI are valid. Anything else falls back to the default.
     */
    fun normalize(minutes: Int): Int =
        minutes.takeIf { it in SELECTABLE_MINUTES } ?: DEFAULT_MINUTES

    fun minutesToMillis(minutes: Int): Long = minutes * 60_000L
}
