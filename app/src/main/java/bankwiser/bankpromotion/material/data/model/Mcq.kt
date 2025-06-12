package bankwiser.bankpromotion.material.data.model

data class Mcq(
    val id: String,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String, // "A", "B", "C", or "D"
    val subCategoryId: String? // Or categoryId
)
