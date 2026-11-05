package de.naturfoto.app.projects

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

class ProjectsRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun myProjects(): List<JsonObject> =
        db["project_members"].select {
            filter { eq("user_id", userId ?: "") }
            order("role", Order.ASCENDING)
        }.decodeList<JsonObject>()
            .mapNotNull { m ->
                db["projects"].select { filter { eq("id", m.str("project_id")) } }
                    .decodeList<JsonObject>().firstOrNull()
                    ?.let { p -> JsonObject(p.toMutableMap().apply { put("my_role", kotlinx.serialization.json.JsonPrimitive(m.str("role"))) }) }
            }

    suspend fun createProject(name: String, description: String?): String {
        db["projects"].insert(
            jsonOf(
                "name" to name,
                "description" to description,
                "creator_id" to (userId ?: ""),
            ),
        ) { select() }
        return db["projects"].select {
            filter { eq("creator_id", userId ?: "") }
            order("created_at", Order.DESCENDING)
        }.decodeList<JsonObject>().first().str("id")
    }

    suspend fun createInvite(projectId: String, role: String = "member"): String {
        val code = java.util.UUID.randomUUID().toString().take(8)
        db["project_invites"].insert(
            jsonOf(
                "project_id" to projectId,
                "code" to code,
                "role" to role,
                "created_by" to (userId ?: ""),
            ),
        )
        return code
    }

    suspend fun leaveProject(projectId: String) {
        db["project_members"].delete {
            filter {
                eq("project_id", projectId)
                eq("user_id", userId ?: "")
            }
        }
    }

    suspend fun members(projectId: String): List<JsonObject> =
        db["project_members"].select {
            filter { eq("project_id", projectId) }
        }.decodeList<JsonObject>()
            .map { m ->
                val profile = db["profiles"].select { filter { eq("id", m.str("user_id")) } }
                    .decodeList<JsonObject>().firstOrNull()
                JsonObject(m.toMutableMap().apply {
                    put("display_name", kotlinx.serialization.json.JsonPrimitive(profile?.str("display_name") ?: "?"))
                })
            }

    suspend fun joinByCode(code: String): Result<String> = runCatching {
        val invite = db["project_invites"].select {
            filter { eq("code", code.trim().lowercase()) }
        }.decodeList<JsonObject>().firstOrNull() ?: error("Ungültiger Einladungscode")
        val expired = invite.str("expires_at").let { it.isNotBlank() && it < kotlinx.datetime.Clock.System.now().toString() }
        if (expired) error("Einladung abgelaufen")
        db["project_members"].upsert(
            jsonOf(
                "project_id" to invite.str("project_id"),
                "user_id" to (userId ?: ""),
                "role" to invite.str("role").ifBlank { "member" },
            ),
        )
        invite.str("project_id")
    }
}
