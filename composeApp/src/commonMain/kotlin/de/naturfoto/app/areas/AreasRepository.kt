package de.naturfoto.app.areas

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Suppress("UNCHECKED_CAST")
class AreasRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun firstProjectIdForUser(): String? {
        val uid = userId ?: return null
        val memberships = db["project_members"].select { filter { eq("user_id", uid) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()
        return memberships.firstOrNull()?.str("project_id")
    }

    suspend fun listAreas(projectId: String): List<kotlinx.serialization.json.JsonObject> =
        db["areas"].select { filter { eq("project_id", projectId) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()

    suspend fun createArea(projectId: String, name: String, description: String?) {
        db["areas"].insert(
            jsonOf(
                "project_id" to projectId,
                "name" to name,
                "description" to description,
            ),
        )
    }

    suspend fun listForums(areaId: String): List<kotlinx.serialization.json.JsonObject> =
        db["forums"].select { filter { eq("area_id", areaId) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()

    suspend fun createForum(areaId: String, name: String) {
        db["forums"].insert(
            jsonOf(
                "area_id" to areaId,
                "name" to name,
                "created_by" to (userId ?: ""),
            ),
        )
    }

    suspend fun listPosts(areaId: String, forumId: String?): List<kotlinx.serialization.json.JsonObject> =
        db["posts"].select {
            filter { eq("area_id", areaId) }
            order("pinned", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<kotlinx.serialization.json.JsonObject>()
            .filter { (it.str("forum_id")) == (forumId ?: "") }

    suspend fun createPost(areaId: String, forumId: String?, title: String, body: String, imageUrl: String? = null) {
        db["posts"].insert(
            jsonOf(
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

    suspend fun listThreads(forumId: String): List<kotlinx.serialization.json.JsonObject> =
        db["threads"].select {
            filter { eq("forum_id", forumId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<kotlinx.serialization.json.JsonObject>()

    suspend fun createThread(forumId: String, title: String, firstPost: String) {
        db["threads"].insert(
            jsonOf(
                "forum_id" to forumId,
                "title" to title,
                "first_post" to firstPost,
                "created_by" to (userId ?: ""),
            ),
        )
    }

    suspend fun listComments(postId: String? = null, threadId: String? = null): List<kotlinx.serialization.json.JsonObject> =
        db["post_comments"].select {
            filter {
                if (postId != null) eq("post_id", postId) else eq("thread_id", threadId ?: "")
            }
            order("created_at", Order.ASCENDING)
        }.decodeList<kotlinx.serialization.json.JsonObject>()

    suspend fun addComment(postId: String? = null, threadId: String? = null, body: String) {
        db["post_comments"].insert(
            jsonOf(
                "post_id" to postId,
                "thread_id" to threadId,
                "author_id" to (userId ?: ""),
                "body" to body,
            ),
        )
    }
}
