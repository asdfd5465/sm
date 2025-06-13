package bankwiser.bankpromotion.material.data.model

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val subCategoryId: String?,
    val isFreeLaunchContent: Boolean, // Added
    val isPremium: Boolean          // Added
)
