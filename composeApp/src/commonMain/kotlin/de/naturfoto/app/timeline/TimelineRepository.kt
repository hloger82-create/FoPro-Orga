package de.naturfoto.app.timeline

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

data class TimelineItem(
    val kind: String, // "post" | "message" | "event"
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val areaName: String,
)

class TimelineRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun subscribedAreaIds(projectId: String): Set<String> {
        val areas = db["areas"].select { filter { eq("project_id", projectId) } }
            .decodeList<JsonObject>()
        val subs = db["area_subscriptions"].select { filter { eq("user_id", userId ?: "") } }
            .decodeList<JsonObject>()
            .associate { it.str("area_id") to (if (it.containsKey("subscribed")) it.bool("subscribed") else true) }
        return areas.map { it.str("id") }.filter { subs[it] != false }.toSet()
    }

    suspend fun load(projectId: String): List<TimelineItem> {
        val areaIds = subscribedAreaIds(projectId)
        if (areaIds.isEmpty()) return emptyList()

        val areas = db["areas"].select { filter { eq("project_id", projectId) } }
            .decodeList<JsonObject>()
            .associate { it.str("id") to it.str("name") }

        val posts = db["posts"].select {
            order("created_at", Order.DESCENDING)
        }.decodeList<JsonObject>().filter { it.str("area_id") in areaIds }

        val channels = db["chat_channels"].select { filter { eq("project_id", projectId) } }
            .decodeList<JsonObject>()
        val channelArea = channels.associate { it.str("id") to it.str("area_id") }
        val channelName = channels.associate { it.str("id") to it.str("name") }
        val muted = db["channel_mutes"].select { filter { eq("user_id", userId ?: "") } }
            .decodeList<JsonObject>()
            .filter { it.bool("muted") }
            .map { it.str("channel_id") }
            .toSet()
        val messages = db["messages"].select {
            order("created_at", Order.DESCENDING)
        }.decodeList<JsonObject>()
            .filter {
                val chArea = channelArea[it.str("channel_id")]
                (chArea == null || chArea in areaIds) && it.str("channel_id") !in muted
            }

        val events = db["events"].select {
            filter { eq("project_id", projectId) }
            order("start_time", Order.DESCENDING)
        }.decodeList<JsonObject>()

        return (posts.map {
            TimelineItem("post", it.str("id"), it.str("title"), it.str("body"), it.str("created_at"), areas[it.str("area_id")] ?: "")
        } + messages.map {
            TimelineItem("message", it.str("id"), channelName[it.str("channel_id")] ?: "Chat", it.str("body"), it.str("created_at"), areas[channelArea[it.str("channel_id")]] ?: "Allgemein")
        } + events.map {
            TimelineItem("event", it.str("id"), "📅 ${it.str("title")}", it.str("start_time").take(16).replace('T', ' '), it.str("created_at"), "")
        }).sortedByDescending { it.createdAt }.take(100)
    }
}

