package com.rhythm.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores user-excluded package names and the "delete all data" flag.
 */
class ExclusionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rhythm_prefs", Context.MODE_PRIVATE)

    /**
     * SharedPreferences hands back its own live Set instance, and mutating or
     * holding on to it is undefined behaviour — always return a copy.
     */
    fun getExcluded(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()

    fun setExcluded(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED, pkgs.toSet()).apply()
    }

    companion object {
        private const val KEY_EXCLUDED = "excluded_packages"
    }
}
