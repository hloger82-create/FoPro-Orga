package de.naturfoto.app.ebird

import de.naturfoto.app.data.str
import kotlinx.serialization.json.JsonObject

object EBirdExporter {
    /**
     * eBird-kompatibles CSV (Minimalformat):
     * Common Name, Latitude, Longitude, Date (YYYY-MM-DD), Time (HH:MM)
     */
    fun toCsv(spots: List<JsonObject>): String {
        val sb = StringBuilder("Common Name,Latitude,Longitude,Date,Time\n")
        for (s in spots) {
            val name = s.str("title").replace(",", ";").replace("\n", " ")
            val lat = s.str("lat")
            val lng = s.str("lng")
            val created = s.str("created_at")
            val date = created.take(10)
            val time = if (created.length >= 16) created.substring(11, 16) else ""
            sb.append("\"$name\",$lat,$lng,$date,$time\n")
        }
        return sb.toString()
    }
}
