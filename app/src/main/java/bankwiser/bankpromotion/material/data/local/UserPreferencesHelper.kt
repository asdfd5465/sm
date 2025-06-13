package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BankWiserUserPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val BOOKMARKED_NOTES_PREFIX = "bookmarked_note_"
        private const val BOOKMARKED_FAQS_PREFIX = "bookmarked_faq_"
        private const val BOOKMARKED_MCQS_PREFIX = "bookmarked_mcq_"
        private const val BOOKMARKED_AUDIO_PREFIX = "bookmarked_audio_"
        // private const val READ_NOTES_PREFIX = "read_note_" // Removed
    }

    // --- Generic Bookmark Functions ---
    private fun isBookmarked(itemId: String, prefix: String): Boolean {
        return prefs.getBoolean(prefix + itemId, false)
    }

    private fun setBookmarked(itemId: String, prefix: String, isBookmarked: Boolean) {
        prefs.edit().putBoolean(prefix + itemId, isBookmarked).apply()
    }

    private fun toggleBookmark(itemId: String, prefix: String): Boolean {
        val currentStatus = isBookmarked(itemId, prefix)
        setBookmarked(itemId, prefix, !currentStatus)
        return !currentStatus
    }

    // --- Notes Bookmarking ---
    fun isNoteBookmarked(noteId: String): Boolean = isBookmarked(noteId, BOOKMARKED_NOTES_PREFIX)
    fun toggleNoteBookmark(noteId: String): Boolean = toggleBookmark(noteId, BOOKMARKED_NOTES_PREFIX)

    // --- FAQs Bookmarking ---
    fun isFaqBookmarked(faqId: String): Boolean = isBookmarked(faqId, BOOKMARKED_FAQS_PREFIX)
    fun toggleFaqBookmark(faqId: String): Boolean = toggleBookmark(faqId, BOOKMARKED_FAQS_PREFIX)

    // --- MCQs Bookmarking ---
    fun isMcqBookmarked(mcqId: String): Boolean = isBookmarked(mcqId, BOOKMARKED_MCQS_PREFIX)
    fun toggleMcqBookmark(mcqId: String): Boolean = toggleBookmark(mcqId, BOOKMARKED_MCQS_PREFIX)

    // --- Audio Bookmarking ---
    fun isAudioBookmarked(audioId: String): Boolean = isBookmarked(audioId, BOOKMARKED_AUDIO_PREFIX)
    fun toggleAudioBookmark(audioId: String): Boolean = toggleBookmark(audioId, BOOKMARKED_AUDIO_PREFIX)


    // --- Read Status for Notes (REMOVED) ---
    // fun isNoteRead(noteId: String): Boolean {
    //     return prefs.getBoolean(READ_NOTES_PREFIX + noteId, false)
    // }
    // fun setNoteRead(noteId: String, isRead: Boolean) {
    //     prefs.edit().putBoolean(READ_NOTES_PREFIX + noteId, isRead).apply()
    // }
    // fun toggleNoteReadStatus(noteId: String): Boolean {
    //     val currentStatus = isNoteRead(noteId)
    //     setNoteRead(noteId, !currentStatus)
    //     return !currentStatus
    // }

    // --- Get All Bookmarked IDs (Example for potential future use) ---
    fun getAllBookmarkedItemIds(prefix: String): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(prefix) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }
    // fun getAllReadNoteIds(): Set<String> { // Removed
    //     return prefs.all.keys
    //         .filter { it.startsWith(READ_NOTES_PREFIX) && prefs.getBoolean(it, false) }
    //         .map { it.removePrefix(READ_NOTES_PREFIX) }
    //         .toSet()
    // }
}
