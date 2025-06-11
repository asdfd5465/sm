package bankwiser.bankpromotion.material.data.model

data class Faq(
    val id: String,
    val question: String,
    val answer: String,
    val subCategoryId: String? // Or categoryId if directly linked
)
