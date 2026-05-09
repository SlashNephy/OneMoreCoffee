package blue.starry.onemorecoffee.core.data.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StarbucksVisitDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("last_visit_date") val lastVisitDate: String? = null,
    @SerialName("first_visit_date") val firstVisitDate: String? = null,
    @SerialName("frequency_of_visits") val frequencyOfVisits: String? = null,
    @SerialName("pref_code") val prefCode: Int? = null,
    val name: String? = null,
    @SerialName("is_exist") val isExist: Int? = null,
)
