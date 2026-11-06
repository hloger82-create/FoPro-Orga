package de.naturfoto.app.theme

import android.content.Context

private const val PREFS = "naturfoto_settings"
private const val KEY_MODE = "theme_mode"

object AppContextHolder {
    lateinit var context: Context
}

actual object ThemeModeStore {
    private fun prefs() = AppContextHolder.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    actual fun get(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs().getString(KEY_MODE, ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)
    actual fun set(mode: ThemeMode) {
        prefs().edit().putString(KEY_MODE, mode.name).apply()
    }
}
