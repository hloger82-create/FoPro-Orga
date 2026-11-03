package de.naturfoto.app.areas

import de.naturfoto.app.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Suppress("UNCHECKED_CAST")
class AreasRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun firstProjectIdForUser(): String? {
        val uid = userId ?: return null
        val memberships = db["project_members"].select { filter { eq("user_id", uid) } }
            .decodeList<Map<String, Any?>>()
        return memberships.firstOrNull()?.get("project_id")?.toString()
    }

    suspend fun listAreas(projectId: String): List<Map<String, Any?>> =
        db["areas"].select { filter { eq("project_id", projectId) } }
            .decodeList<Map<String, Any?>>()

    suspend fun createArea(projectId: String, name: String, description: String?) {
        db["areas"].insert(
            mapOf(
                "project_id" to projectId,
                "name" to name,
                "description" to description,
            ),
        )
    }

    suspend fun listForums(areaId: String): List<Map<String, Any?>> =
        db["forums"].select { filter { eq("area_id", areaId) } }
            .decodeList<Map<String, Any?>>()

    suspend fun createForum(areaId: String, name: String) {
        db["forums"].insert(
            mapOf(
                "area_id" to areaId,
                "name" to name,
                "created_by" to (userId ?: ""),
            ),
        )
    }

    suspend fun listPosts(areaId: String, forumId: String?): List<Map<String, Any?>> =
        db["posts"].select {
            filter {
                eq("area_id", areaId)
                if (forumId == null) eq("forum_id", null) else eq("forum_id", forumId)
            }
            order("pinned", false)
            order("created_at", false)
        }.decodeList<Map<String, Any?>>()

    suspend fun createPost(areaId: String, forumId: String?, title: String, body: String, imageUrl: String? = null) {
        db["posts"].insert(
            mapOf(
                "area_id" to areaId,
                "forum_id" to forumId,
                "author_id" to (userId ?: ""),
                "title" to title,
                "body" to body,
                "image_url" to imageUrl,
            ),
        )
    }

    suspend fun deletePost(postId: String) {
        db["posts"].delete { filter { eq("id", postId) } }
    }
}
