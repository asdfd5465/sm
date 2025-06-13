package bankwiser.bankpromotion.material.data.model

data class AudioContent(
    val id: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Int?,
    val subCategoryId: String?,
    val isFreeLaunchContent: Boolean, // Added
    val isPremium: Boolean          // Added
)
