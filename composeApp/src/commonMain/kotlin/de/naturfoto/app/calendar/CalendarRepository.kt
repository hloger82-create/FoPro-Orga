package de.naturfoto.app.calendar

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

class CalendarRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun listEvents(projectId: String): List<JsonObject> =
        db["events"].select {
            filter { eq("project_id", projectId) }
            order("start_time", Order.ASCENDING)
        }.decodeList<JsonObject>()

    suspend fun createEvent(
        projectId: String,
        title: String,
        description: String?,
        kind: String,
        locationName: String?,
        lat: Double?,
        lng: Double?,
        start: String,
        end: String?,
        maxParticipants: Int?,
    ) {
        db["events"].insert(
            mapOf(
                "project_id" to projectId,
                "title" to title,
                "description" to description,
                "kind" to kind,
                "location_name" to locationName,
                "location_lat" to lat,
                "location_lng" to lng,
                "start_time" to start,
                "end_time" to end,
                "max_participants" to maxParticipants,
                "created_by" to (userId ?: ""),
            ),
        )
    }

    suspend fun myRsvp(eventId: String): String? =
        db["event_rsvps"].select {
            filter {
                eq("event_id", eventId)
                eq("user_id", userId ?: "")
            }
        }.decodeList<JsonObject>().firstOrNull()?.str("status")

    suspend fun rsvpCounts(eventId: String): Map<String, Int> =
        db["event_rsvps"].select { filter { eq("event_id", eventId) } }
            .decodeList<JsonObject>()
            .groupingBy { it.str("status") }
            .eachCount()

    suspend fun setRsvp(eventId: String, status: String) {
        db["event_rsvps"].upsert(
            mapOf(
                "event_id" to eventId,
                "user_id" to (userId ?: ""),
                "status" to status,
            ),
        )
    }

    suspend fun listComments(eventId: String): List<JsonObject> =
        db["event_comments"].select {
            filter { eq("event_id", eventId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<JsonObject>()

    suspend fun addComment(eventId: String, body: String) {
        db["event_comments"].insert(
            mapOf(
                "event_id" to eventId,
                "author_id" to (userId ?: ""),
                "body" to body,
            ),
        )
    }
}
