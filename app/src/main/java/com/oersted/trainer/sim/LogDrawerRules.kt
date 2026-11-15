package com.oersted.trainer.sim

/**
 * Rules behind the LOG drawer (routes/cockpit.tsx LogDrawer, lines 356-421).
 * The drawer lists the sim's event log; these functions give the row labels so the Compose view stays dumb.
 */
object LogDrawerRules {
    /** Shown when the log is empty (web: "No events recorded yet. Operate a cockpit or training control."). */
    const val EMPTY_TEXT = "No events recorded yet. Operate a cockpit or training control."

    /** Web: e.control ?? "SYSTEM". */
    fun rowTitle(control: String?): String = control ?: "SYSTEM"

    /** Web: the CAUSE / COMMAND line, shown only for control actions. Missing values read as an em dash. */
    fun causeLine(entry: LogEntry): String? {
        if (entry.control == null) return null
        val before = entry.before ?: "\u2014"
        val after = entry.after ?: "\u2014"
        return "CAUSE: $before \u2192 COMMAND: $after"
    }

    /** True when the drawer shows the empty-state panel instead of rows. */
    fun isEmpty(log: List<LogEntry>): Boolean = log.isEmpty()
}
