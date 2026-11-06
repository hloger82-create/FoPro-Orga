package de.naturfoto.app.areas

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.str
import de.naturfoto.app.data.textOrNull
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Suppress("UNCHECKED_CAST")
class AreasRepository {
    private val db get() = Supabase.client.postgrest
    val userIdSafe: String? get() = userId
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

    suspend fun isSubscribed(areaId: String): Boolean =
        db["area_subscriptions"].select {
            filter {
                eq("area_id", areaId)
                eq("user_id", userId ?: "")
            }
        }.decodeList<kotlinx.serialization.json.JsonObject>()
            .firstOrNull()?.let { if (it.containsKey("subscribed")) it.bool("subscribed") else true } ?: true

    suspend fun setSubscribed(areaId: String, subscribed: Boolean) {
        db["area_subscriptions"].upsert(
            jsonOf(
                "area_id" to areaId,
                "user_id" to (userId ?: ""),
                "subscribed" to subscribed,
            ),
        )
    }

    suspend fun listForums(areaId: String): List<kotlinx.serialization.json.JsonObject> =
        db["forums"].select { filter { eq("area_id", areaId) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()

    suspend fun projectMembers(projectId: String): List<kotlinx.serialization.json.JsonObject> {
        val members = db["project_members"].select { filter { eq("project_id", projectId) } }
            .decodeList<kotlinx.serialization.json.JsonObject>()
        return members.map { m ->
            val prof = db["profiles"].select { filter { eq("id", m.str("user_id")) } }
                .decodeList<kotlinx.serialization.json.JsonObject>().firstOrNull()
            kotlinx.serialization.json.JsonObject(m.toMutableMap().apply {
                put("display_name", kotlinx.serialization.json.JsonPrimitive(prof?.str("display_name") ?: "?"))
            })
        }
    }

    suspend fun projectIdOfArea(areaId: String): String? =
        db["areas"].select { filter { eq("id", areaId) } }.decodeList<kotlinx.serialization.json.JsonObject>()
            .firstOrNull()?.str("project_id")

    suspend fun areaAndProjectNames(areaId: String): Pair<String, String> {
        val area = db["areas"].select { filter { eq("id", areaId) } }.decodeList<kotlinx.serialization.json.JsonObject>().first()
        val project = db["projects"].select { filter { eq("id", area.str("project_id")) } }.decodeList<kotlinx.serialization.json.JsonObject>().first()
        return area.str("name") to project.str("name")
    }

    suspend fun forumForArea(areaId: String): String? =
        listForums(areaId).firstOrNull()?.str("id")

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

    suspend fun createPost(areaId: String, forumId: String?, title: String, body: String, imageUrl: String? = null, audience: String = "area", recipientId: String? = null) {
        db["posts"].insert(
            jsonOf(
                "area_id" to areaId,
                "forum_id" to forumId,
                "author_id" to (userId ?: ""),
                "title" to title,
                "body" to body,
                "image_url" to imageUrl,
                "audience" to audience,
                "recipient_id" to recipientId,
            ),
        )
    }

    suspend fun deletePost(postId: String) {
        db["posts"].delete { filter { eq("id", postId) } }
    }

    suspend fun feedForArea(areaId: String): List<kotlinx.serialization.json.JsonObject> {
        val forumId = forumForArea(areaId) ?: return listPosts(areaId, null)
        val posts = listPosts(areaId, null).map { p ->
            kotlinx.serialization.json.JsonObject(p.toMutableMap().apply { put("kind", kotlinx.serialization.json.JsonPrimitive("post")) })
        }
        val threads = listThreads(forumId).map { t ->
            kotlinx.serialization.json.JsonObject(t.toMutableMap().apply {
                put("kind", kotlinx.serialization.json.JsonPrimitive("thread"))
                put("title", t["title"] ?: kotlinx.serialization.json.JsonNull)
                put("body", kotlinx.serialization.json.JsonPrimitive(str2(t, "first_post")))
                put("area_id", kotlinx.serialization.json.JsonPrimitive(areaId))
            })
        }
        return (posts + threads).sortedByDescending { str2(it, "created_at") }
    }

    private fun str2(o: kotlinx.serialization.json.JsonObject, k: String): String =
        (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.textOrNull ?: ""

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

    suspend fun addComment(postId: String? = null, threadId: String? = null, body: String, replyTo: String? = null, quotedText: String? = null) {
        db["post_comments"].insert(
            jsonOf(
                "post_id" to postId,
                "thread_id" to threadId,
                "author_id" to (userId ?: ""),
                "body" to body,
                "reply_to" to replyTo,
                "quoted_text" to quotedText,
            ),
        )
    }
}
