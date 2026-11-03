package de.naturfoto.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object Supabase {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfigFields.SUPABASE_URL,
            supabaseKey = BuildConfigFields.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}

object BuildConfigFields {
    const val SUPABASE_URL = "https://<projekt-ref>.supabase.co"
    const val SUPABASE_ANON_KEY = "<anon-key>"
}
