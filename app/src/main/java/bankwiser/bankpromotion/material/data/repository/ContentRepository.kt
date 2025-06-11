package bankwiser.bankpromotion.material.data.repository

import bankwiser.bankpromotion.material.data.local.DatabaseHelper

class ContentRepository(private val dbHelper: DatabaseHelper) {
    fun getAllCategories() = dbHelper.getAllCategories()
    fun getSubCategories(categoryId: String) = dbHelper.getSubCategories(categoryId)
    
    // Notes
    fun getNotes(subCategoryId: String) = dbHelper.getNotes(subCategoryId)
    fun getNote(noteId: String) = dbHelper.getNote(noteId)

    // FAQs
    fun getFaqs(subCategoryId: String) = dbHelper.getFaqs(subCategoryId)

    // MCQs
    fun getMcqs(subCategoryId: String) = dbHelper.getMcqs(subCategoryId)

    // Audio
    fun getAudioContent(subCategoryId: String) = dbHelper.getAudioContent(subCategoryId)

    // Search
    fun searchNotesByTitle(query: String) = dbHelper.searchNotesByTitle(query)
    fun searchFaqsByQuestion(query: String) = dbHelper.searchFaqsByQuestion(query)
    // Add more search methods later
}
