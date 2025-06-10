package bankwiser.bankpromotion.material.data.repository

import bankwiser.bankpromotion.material.data.local.ContentDatabase

class ContentRepository(private val db: ContentDatabase) {

    // Standard data access functions
    fun getAllCategories() = db.categoryDao().getAllCategories()
    fun getSubCategories(categoryId: String) = db.subCategoryDao().getSubCategoriesForCategory(categoryId)
    fun getNotes(subCategoryId: String) = db.noteDao().getNotesForSubCategory(subCategoryId)
    fun getNote(noteId: String) = db.noteDao().getNoteById(noteId)
}
