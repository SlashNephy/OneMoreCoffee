package blue.starry.onemorecoffee.feature.settings

data class SettingsUiState(
    val isLoading: Boolean = false,
    val progressMessage: String? = null,
    val statusMessage: String? = null,
)
