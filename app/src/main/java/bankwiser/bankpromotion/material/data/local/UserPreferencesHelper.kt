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
        private const val IS_SUBSCRIBED_KEY = "is_user_subscribed" // New key
    }

    // --- Subscription Status (Simulated) --- todo
    fun isUserSubscribed(): Boolean {
        // In a debug build, you could override this with BuildConfig.FORCE_SUBSCRIBED_DEBUG
        return prefs.getBoolean(IS_SUBSCRIBED_KEY, false) // Default to not subscribed
    }

    fun setUserSubscribed(isSubscribed: Boolean) {
        prefs.edit().putBoolean(IS_SUBSCRIBED_KEY, isSubscribed).apply()
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

    // --- Get All Bookmarked IDs (Example for potential future use) ---
    fun getAllBookmarkedItemIds(prefix: String): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(prefix) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }
}
