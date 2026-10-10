package com.localdex

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "localdex_prefs"
    private const val KEY_HAS_PAIRED = "has_paired_before"
    private const val KEY_DISPLAY_SPEC = "display_spec"

    const val DEFAULT_DISPLAY_SPEC = "1920x1440/240"
    private val DISPLAY_SPEC_PATTERN = Regex("[0-9]{3,4}x[0-9]{3,4}/[0-9]{2,3}")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPairedBefore(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_PAIRED, false)

    fun setHasPairedBefore(context: Context) {
        prefs(context).edit().putBoolean(KEY_HAS_PAIRED, true).apply()
    }

    fun isValidDisplaySpec(spec: String): Boolean = DISPLAY_SPEC_PATTERN.matches(spec)

    fun getDisplaySpec(context: Context): String {
        val saved = prefs(context).getString(KEY_DISPLAY_SPEC, null)
        return if (saved != null && isValidDisplaySpec(saved)) saved else DEFAULT_DISPLAY_SPEC
    }

    fun setDisplaySpec(context: Context, spec: String) {
        require(isValidDisplaySpec(spec)) { "Invalid display specification" }
        prefs(context).edit().putString(KEY_DISPLAY_SPEC, spec).apply()
    }
}
