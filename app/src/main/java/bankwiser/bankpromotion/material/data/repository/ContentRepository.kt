package bankwiser.bankpromotion.material.data.repository

import bankwiser.bankpromotion.material.data.local.DatabaseHelper

class ContentRepository(private val dbHelper: DatabaseHelper) {
    fun getAllCategories() = dbHelper.getAllCategories()
    fun getSubCategories(categoryId: String) = dbHelper.getSubCategories(categoryId)
    fun getNotes(subCategoryId: String) = dbHelper.getNotes(subCategoryId)
    fun getNote(noteId: String) = dbHelper.getNote(noteId)
}
