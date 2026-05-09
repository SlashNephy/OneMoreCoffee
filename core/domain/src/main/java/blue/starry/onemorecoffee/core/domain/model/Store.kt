package blue.starry.onemorecoffee.core.domain.model

data class Store(
    val id: String,
    val name: String,
    val nameEn: String?,
    val prefCode: String,
    val prefecture: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val isReserve: Boolean,
)
