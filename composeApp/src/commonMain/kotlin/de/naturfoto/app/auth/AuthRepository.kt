package de.naturfoto.app.auth

import de.naturfoto.app.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {
    private val auth get() = Supabase.client.auth

    suspend fun signUp(email: String, password: String, username: String, displayName: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("username", username)
                put("display_name", displayName)
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun resetPassword(email: String) = auth.resetPasswordForEmail(email)

    suspend fun signOut() = auth.signOut()

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
    fun emailConfirmed(): Boolean = auth.currentUserOrNull()?.emailConfirmedAt != null
}
