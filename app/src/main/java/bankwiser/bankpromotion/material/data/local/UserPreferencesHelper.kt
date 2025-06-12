package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BankWiserUserPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val BOOKMARKED_NOTES_PREFIX = "bookmarked_note_"
        private const val READ_NOTES_PREFIX = "read_note_"
    }

    // --- Bookmarking Notes ---
    fun isNoteBookmarked(noteId: String): Boolean {
        return prefs.getBoolean(BOOKMARKED_NOTES_PREFIX + noteId, false)
    }

    fun setNoteBookmarked(noteId: String, isBookmarked: Boolean) {
        prefs.edit().putBoolean(BOOKMARKED_NOTES_PREFIX + noteId, isBookmarked).apply()
    }

    fun toggleNoteBookmark(noteId: String): Boolean {
        val currentStatus = isNoteBookmarked(noteId)
        setNoteBookmarked(noteId, !currentStatus)
        return !currentStatus
    }

    // --- Read Status for Notes ---
    fun isNoteRead(noteId: String): Boolean {
        return prefs.getBoolean(READ_NOTES_PREFIX + noteId, false)
    }

    fun setNoteRead(noteId: String, isRead: Boolean) {
        prefs.edit().putBoolean(READ_NOTES_PREFIX + noteId, isRead).apply()
    }

    fun toggleNoteReadStatus(noteId: String): Boolean {
        val currentStatus = isNoteRead(noteId)
        setNoteRead(noteId, !currentStatus)
        return !currentStatus
    }

    // --- Get All Bookmarked/Read IDs (Example for potential future use) ---
    fun getAllBookmarkedNoteIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(BOOKMARKED_NOTES_PREFIX) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(BOOKMARKED_NOTES_PREFIX) }
            .toSet()
    }

    fun getAllReadNoteIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(READ_NOTES_PREFIX) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(READ_NOTES_PREFIX) }
            .toSet()
    }
}
