package blue.starry.onemorecoffee.core.ui

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface StoreBusinessHoursText {
    data class Open(val text: String) : StoreBusinessHoursText

    data class Closed(val nextOpeningText: String) : StoreBusinessHoursText

    data object Unknown : StoreBusinessHoursText
}

internal fun storeBusinessHoursText(
    rawJson: String,
    now: ZonedDateTime = ZonedDateTime.now(StoreBusinessHoursZone),
): StoreBusinessHoursText {
    val weeklyHours = runCatching {
        Json.parseToJsonElement(rawJson) as? JsonObject
    }.getOrNull()?.toWeeklyHours() ?: return StoreBusinessHoursText.Unknown
    val todayHours = weeklyHours[now.dayOfWeek]

    if (todayHours != null && todayHours.contains(now.toLocalTime())) {
        return StoreBusinessHoursText.Open(todayHours.displayText)
    }

    val nextOpening = (0..6).firstNotNullOfOrNull { dayOffset ->
        val date = now.toLocalDate().plusDays(dayOffset.toLong())
        val dayOfWeek = date.dayOfWeek
        val hours = weeklyHours[dayOfWeek] ?: return@firstNotNullOfOrNull null
        if (dayOffset == 0 && !now.toLocalTime().isBefore(hours.open)) {
            return@firstNotNullOfOrNull null
        }

        "営業開始: ${hours.open.displayText()} (${dayOfWeek.label})"
    }

    return nextOpening?.let(StoreBusinessHoursText::Closed) ?: StoreBusinessHoursText.Unknown
}

private fun JsonObject.toWeeklyHours(): Map<DayOfWeek, BusinessHours> {
    return DayOfWeek.entries.mapNotNull { dayOfWeek ->
        val hours = businessDayHours(dayOfWeek) ?: openCloseHours(dayOfWeek) ?: return@mapNotNull null
        dayOfWeek to hours
    }.toMap()
}

private fun JsonObject.businessDayHours(dayOfWeek: DayOfWeek): BusinessHours? {
    val key = when (dayOfWeek) {
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        -> "business_day_mon_thu"
        DayOfWeek.FRIDAY -> "business_day_fri"
        DayOfWeek.SATURDAY -> "business_day_sat"
        DayOfWeek.SUNDAY -> "business_day_sun"
    }
    val value = firstString(key) ?: return null
    val parts = value.split("～", "~").map { part -> part.trim() }
    if (parts.size != 2) {
        return null
    }

    return BusinessHours.from(parts[0], parts[1])
}

private fun JsonObject.openCloseHours(dayOfWeek: DayOfWeek): BusinessHours? {
    val prefix = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "mon"
        DayOfWeek.TUESDAY -> "tue"
        DayOfWeek.WEDNESDAY -> "wed"
        DayOfWeek.THURSDAY -> "thu"
        DayOfWeek.FRIDAY -> "fri"
        DayOfWeek.SATURDAY -> "sat"
        DayOfWeek.SUNDAY -> "sun"
    }

    return BusinessHours.from(
        openText = firstString("${prefix}_open"),
        closeText = firstString("${prefix}_close"),
    )
}

private fun JsonObject.firstString(key: String): String? {
    return get(key)?.firstPrimitiveContent()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonElement.firstPrimitiveContent(): String? {
    return when (this) {
        is JsonPrimitive -> content
        is JsonArray -> firstNotNullOfOrNull { element -> element.firstPrimitiveContent() }
        else -> null
    }
}

private data class BusinessHours(
    val open: LocalTime,
    val close: LocalTime,
) {
    val displayText: String
        get() = "${open.displayText()} ~ ${close.displayText()}"

    fun contains(time: LocalTime): Boolean {
        return if (close.isAfter(open)) {
            !time.isBefore(open) && time.isBefore(close)
        } else {
            !time.isBefore(open) || time.isBefore(close)
        }
    }

    companion object {
        fun from(
            openText: String?,
            closeText: String?,
        ): BusinessHours? {
            val open = openText?.toLocalTimeOrNull() ?: return null
            val close = closeText?.toLocalTimeOrNull() ?: return null
            return BusinessHours(open = open, close = close)
        }
    }
}

private fun String.toLocalTimeOrNull(): LocalTime? {
    return runCatching { LocalTime.parse(this, BusinessHoursTimeFormatter) }.getOrNull()
}

private fun LocalTime.displayText(): String {
    return format(BusinessHoursTimeFormatter)
}

private val DayOfWeek.label: String
    get() = getDisplayName(TextStyle.SHORT, Locale.JAPANESE)

private val StoreBusinessHoursZone = ZoneId.of("Asia/Tokyo")
private val BusinessHoursTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
