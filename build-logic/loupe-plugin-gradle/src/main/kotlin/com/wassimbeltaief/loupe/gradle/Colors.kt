package com.wassimbeltaief.loupe.gradle

/**
 * Minimal ANSI colour helper for the results table.
 *
 * Colours are enabled only when writing to a real terminal (and `NO_COLOR` is
 * unset), so captured CI logs stay plain text. Tests construct it directly with
 * `Colors(enabled = false)`.
 */
internal class Colors(private val enabled: Boolean) {

    fun red(text: String) = wrap("\u001B[31m", text)
    fun green(text: String) = wrap("\u001B[32m", text)
    fun yellow(text: String) = wrap("\u001B[33m", text)

    private fun wrap(code: String, text: String) =
        if (enabled) "$code$text\u001B[0m" else text

    companion object {
        fun auto(): Colors {
            if (System.getenv("NO_COLOR") != null) return Colors(enabled = false)
            if (System.getenv("FORCE_COLOR") != null) return Colors(enabled = true)
            val term = System.getenv("TERM") ?: ""
            val colorTerm = System.getenv("COLORTERM") ?: ""
            return Colors(enabled = (term.isNotBlank() && term != "dumb") || colorTerm.isNotBlank())
        }
    }
}