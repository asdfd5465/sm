package bankwiser.bankpromotion.material.data.model

data class Mcq(
    val id: String,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String,
    val subCategoryId: String?,
    val isFreeLaunchContent: Boolean, // Added
    val isPremium: Boolean          // Added
)
