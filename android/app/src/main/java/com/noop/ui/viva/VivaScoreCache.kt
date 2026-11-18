package com.noop.ui.viva

import android.content.Context

/**
 * The last figure each of today's scores showed, so a reopened app paints it at once while the fresh
 * read runs behind it, as the reference app shows its scores instantly once the morning's are in.
 * Display only: the fresh read always replaces it, and one entry per kind is kept, for its own day alone.
 */
internal object VivaScoreCache {
    private const val PREFS = "viva_score_cache"

    fun get(context: Context, kind: String, day: String): Double? =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(kind, null)
                ?.split('|')?.takeIf { it.size == 2 && it[0] == day }?.get(1)?.toDoubleOrNull()
        }.getOrNull()

    fun put(context: Context, kind: String, day: String, value: Double?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value == null) edit.remove(kind) else edit.putString(kind, "$day|$value")
        edit.apply()
    }
}
