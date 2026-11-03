package de.naturfoto.app.chat

import de.naturfoto.app.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("UNCHECKED_CAST")
class ChatRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun listChannels(projectId: String): List<Map<String, Any?>> =
        db["chat_channels"].select { filter { eq("project_id", projectId) } }
            .decodeList<Map<String, Any?>>()

    suspend fun listMessages(channelId: String): List<Map<String, Any?>> =
        db["messages"].select {
            filter { eq("channel_id", channelId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<Map<String, Any?>>()

    suspend fun sendMessage(channelId: String, body: String, imageUrl: String? = null) {
        db["messages"].insert(
            mapOf(
                "channel_id" to channelId,
                "author_id" to (userId ?: ""),
                "body" to body,
                "image_url" to imageUrl,
            ),
        )
    }

    suspend fun setMuted(channelId: String, muted: Boolean) {
        db["channel_mutes"].upsert(
            mapOf(
                "channel_id" to channelId,
                "user_id" to (userId ?: ""),
                "muted" to muted,
            ),
        )
    }

    suspend fun isMuted(channelId: String): Boolean =
        db["channel_mutes"].select {
            filter {
                eq("channel_id", channelId)
                eq("user_id", userId ?: "")
            }
        }.decodeList<Map<String, Any?>>().firstOrNull()?.get("muted") as? Boolean ?: false

    fun subscribeMessages(
        channelId: String,
        flow: MutableStateFlow<List<Map<String, Any?>>>,
    ): RealtimeChannel? {
        return try {
            val channel = Supabase.client.realtime.channel("messages:$channelId")
            channel.subscribe()
            channel
        } catch (e: Exception) {
            null
        }
    }
}
