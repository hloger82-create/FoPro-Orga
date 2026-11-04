package de.naturfoto.app.surveys

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.list
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

class SurveyRepository {
    private val db get() = Supabase.client.postgrest
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun listSurveys(projectId: String): List<JsonObject> =
        db["surveys"].select {
            filter { eq("project_id", projectId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<JsonObject>()

    suspend fun createSurvey(
        projectId: String,
        scope: String,
        areaId: String?,
        question: String,
        options: List<String>,
        multipleChoice: Boolean,
    ) {
        db["surveys"].insert(
            jsonOf(
                "scope" to scope,
                "project_id" to projectId,
                "area_id" to areaId,
                "question" to question,
                "options" to options,
                "multiple_choice" to multipleChoice,
                "created_by" to (userId ?: ""),
            ),
        )
    }

    suspend fun myVotes(surveyId: String): Set<Int> =
        db["survey_votes"].select {
            filter {
                eq("survey_id", surveyId)
                eq("user_id", userId ?: "")
            }
        }.decodeList<JsonObject>().firstOrNull()
            ?.let { (it["option_indexes"] as? kotlinx.serialization.json.JsonArray) }
            ?.mapNotNull { el -> (el as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> p.contentOrNull?.toIntOrNull() } }
            ?.toSet() ?: emptySet()

    suspend fun voteCounts(surveyId: String): Map<Int, Int> =
        db["survey_votes"].select { filter { eq("survey_id", surveyId) } }
            .decodeList<JsonObject>()
            .flatMap { v ->
                ((v["option_indexes"] as? kotlinx.serialization.json.JsonArray) ?: return@flatMap emptyList())
                    .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull() }
            }
            .groupingBy { it }.eachCount()

    suspend fun setVote(surveyId: String, indexes: List<Int>) {
        db["survey_votes"].upsert(
            jsonOf(
                "survey_id" to surveyId,
                "user_id" to (userId ?: ""),
                "option_indexes" to indexes,
            ),
        )
    }
}
