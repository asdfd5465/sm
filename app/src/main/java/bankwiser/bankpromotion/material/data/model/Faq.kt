package bankwiser.bankpromotion.material.data.model

data class Faq(
    val id: String,
    val question: String,
    val answer: String,
    val subCategoryId: String?,
    val isFreeLaunchContent: Boolean, // Added
    val isPremium: Boolean          // Added
)
