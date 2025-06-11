package bankwiser.bankpromotion.material.data.model

data class AudioContent(
    val id: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Int?,
    val subCategoryId: String? // Or categoryId
)
