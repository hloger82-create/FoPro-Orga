package de.naturfoto.app.theme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

expect object ThemeModeStore {
    fun get(): ThemeMode
    fun set(mode: ThemeMode)
}

expect object ReadStoreHolder {
    fun readIds(): Set<String>
    fun markRead(id: String)
}
