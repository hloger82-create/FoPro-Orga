package de.naturfoto.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectRole { ADMIN, AREA_MODERATOR, MEMBER }

@Serializable
enum class SpotVisibility { PROJECT, AREA_ONLY, PRIVATE }

@Serializable
data class ExifData(
    val camera: String? = null,
    val lens: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val iso: Int? = null,
    val focalLength: String? = null,
)

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val homepage: String? = null,
    val socials: Map<String, String> = emptyMap(),
    val skillTags: List<String> = emptyList(),
    val memberSince: String? = null,
    val showHomepagePublicly: Boolean = false,
    val showSocialsPublicly: Boolean = false,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val creatorId: String,
    val regionPolygon: List<Pair<Double, Double>> = emptyList(),
    val ebirdEnabled: Boolean = false,
)

@Serializable
data class Area(
    val id: String,
    val projectId: String,
    val name: String,
    val description: String? = null,
    val subscribed: Boolean = true,
)

@Serializable
data class Forum(
    val id: String,
    val areaId: String,
    val name: String,
    val createdBy: String,
)

@Serializable
data class PinPost(
    val id: String,
    val areaId: String,
    val authorId: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val pinned: Boolean = false,
    val createdAt: String,
)

@Serializable
data class Message(
    val id: String,
    val channelId: String,
    val authorId: String,
    val body: String,
    val imageUrl: String? = null,
    val createdAt: String,
)

@Serializable
enum class EventKind { ONLINE, ON_SITE }

@Serializable
enum class RsvpStatus { YES, NO, MAYBE }

@Serializable
data class Event(
    val id: String,
    val projectId: String,
    val title: String,
    val description: String? = null,
    val kind: EventKind,
    val locationName: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val mapLink: String? = null,
    val start: String,
    val end: String? = null,
    val maxParticipants: Int? = null,
    val createdBy: String,
)

@Serializable
data class EventInvite(
    val eventId: String,
    val userId: String? = null,
    val areaId: String? = null,
)

@Serializable
data class EventRsvp(
    val eventId: String,
    val userId: String,
    val status: RsvpStatus,
)

@Serializable
data class MapSpot(
    val id: String,
    val projectId: String,
    val authorId: String,
    val title: String,
    val description: String? = null,
    val lat: Double,
    val lng: Double,
    val photoUrl: String? = null,
    val exif: ExifData? = null,
    val visibility: SpotVisibility = SpotVisibility.PROJECT,
    val createdAt: String,
)

@Serializable
enum class SurveyScope { PROJECT, AREA }

@Serializable
data class Survey(
    val id: String,
    val scope: SurveyScope,
    val projectId: String,
    val areaId: String? = null,
    val question: String,
    val options: List<String>,
    val multipleChoice: Boolean = false,
    val createdBy: String,
    val createdAt: String,
)

@Serializable
data class SurveyVote(
    val surveyId: String,
    val userId: String,
    val optionIndexes: List<Int>,
)
