package de.naturfoto.app.data

import de.naturfoto.app.BuildConfig

actual object BuildConfigFields {
    actual val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    actual val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
}
