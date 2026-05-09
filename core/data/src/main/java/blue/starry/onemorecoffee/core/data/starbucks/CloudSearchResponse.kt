package blue.starry.onemorecoffee.core.data.starbucks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CloudSearchResponse(
    val hits: Hits,
) {
    @Serializable
    data class Hits(
        val found: Int,
        val start: Int,
        val hit: List<Hit> = emptyList(),
    )

    @Serializable
    data class Hit(
        val id: String,
        @SerialName("fields") val fields: JsonObject,
    )
}
