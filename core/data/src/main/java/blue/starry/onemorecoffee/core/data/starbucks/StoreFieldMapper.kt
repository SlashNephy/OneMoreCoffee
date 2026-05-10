package blue.starry.onemorecoffee.core.data.starbucks

import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object StoreFieldMapper {
    fun toEntity(
        fields: JsonObject,
        rawJson: String,
        now: Instant = Instant.now(),
    ): StoreEntity? {
        val storeId = fields.firstString("store_id") ?: return null
        val name = fields.firstString("name") ?: return null
        val location = fields.firstString("location")?.toLocation() ?: return null
        val prefCode = fields.firstString("pref_code") ?: return null
        val prefecture = fields.firstString("address_1") ?: return null
        val fullAddress = fields.firstString("address_5") ?: return null

        return StoreEntity(
            id = storeId,
            name = name,
            nameEn = fields.firstString("en_name"),
            prefCode = prefCode,
            prefecture = prefecture,
            fullAddress = fullAddress,
            latitude = location.latitude,
            longitude = location.longitude,
            isReserve = fields.firstString("reserve_flg") == "1",
            rawJson = rawJson,
            lastSeenAt = now,
        )
    }

    private fun JsonObject.firstString(key: String): String? {
        return get(key)?.firstPrimitiveContent()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonElement.firstPrimitiveContent(): String? {
        return when (this) {
            is JsonPrimitive -> runCatching { content }.getOrNull()
            is JsonArray -> firstNotNullOfOrNull { element -> element.firstPrimitiveContent() }
            else -> null
        }
    }

    private fun String.toLocation(): Location? {
        val parts = split(",").map { part -> part.trim() }
        if (parts.size != 2) {
            return null
        }

        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }

        return Location(latitude = latitude, longitude = longitude)
    }

    private data class Location(
        val latitude: Double,
        val longitude: Double,
    )
}
