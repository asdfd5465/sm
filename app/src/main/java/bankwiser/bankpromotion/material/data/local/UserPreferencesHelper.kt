package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Define constants for theme preference
const val THEME_PREFERENCE_KEY = "theme_preference"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"
const val THEME_SYSTEM = "system" // Default

class UserPreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BankWiserUserPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val BOOKMARKED_NOTES_PREFIX = "bookmarked_note_"
        private const val BOOKMARKED_FAQS_PREFIX = "bookmarked_faq_"
        private const val BOOKMARKED_MCQS_PREFIX = "bookmarked_mcq_"
        private const val BOOKMARKED_AUDIO_PREFIX = "bookmarked_audio_"

        private const val IS_SUBSCRIBED_KEY = "is_user_subscribed"
        private const val CURRENT_DB_VERSION_KEY = "current_db_version"
        const val DEFAULT_BUNDLED_DB_VERSION = 1
    }

    // --- Theme Preference ---
    private val _themePreferenceFlow = MutableStateFlow(getThemePreference())
    val themePreferenceFlow: StateFlow<String> = _themePreferenceFlow

    fun getThemePreference(): String {
        return prefs.getString(THEME_PREFERENCE_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemePreference(themeValue: String) {
        prefs.edit().putString(THEME_PREFERENCE_KEY, themeValue).apply()
        _themePreferenceFlow.value = themeValue // Update the flow
    }


    // --- Database Version ---
    fun getCurrentDatabaseVersion(): Int {
        return prefs.getInt(CURRENT_DB_VERSION_KEY, DEFAULT_BUNDLED_DB_VERSION)
    }

    fun setCurrentDatabaseVersion(version: Int) {
        prefs.edit().putInt(CURRENT_DB_VERSION_KEY, version).apply()
    }

    // --- Subscription Status (Simulated) ---
    private val _isUserSubscribedFlow = MutableStateFlow(isUserSubscribed())
    val isUserSubscribedFlow: StateFlow<Boolean> = _isUserSubscribedFlow
    fun isUserSubscribed(): Boolean {
        return prefs.getBoolean(IS_SUBSCRIBED_KEY, false)
    }

    fun setUserSubscribed(isSubscribed: Boolean) {
        prefs.edit().putBoolean(IS_SUBSCRIBED_KEY, isSubscribed).apply()
        _isUserSubscribedFlow.value = isSubscribed // Update the flow
    }

    // --- Bookmark Functions ---
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
    fun isNoteBookmarked(noteId: String): Boolean = isBookmarked(noteId, BOOKMARKED_NOTES_PREFIX)
    fun toggleNoteBookmark(noteId: String): Boolean = toggleBookmark(noteId, BOOKMARKED_NOTES_PREFIX)
    fun isFaqBookmarked(faqId: String): Boolean = isBookmarked(faqId, BOOKMARKED_FAQS_PREFIX)
    fun toggleFaqBookmark(faqId: String): Boolean = toggleBookmark(faqId, BOOKMARKED_FAQS_PREFIX)
    fun isMcqBookmarked(mcqId: String): Boolean = isBookmarked(mcqId, BOOKMARKED_MCQS_PREFIX)
    fun toggleMcqBookmark(mcqId: String): Boolean = toggleBookmark(mcqId, BOOKMARKED_MCQS_PREFIX)
    fun isAudioBookmarked(audioId: String): Boolean = isBookmarked(audioId, BOOKMARKED_AUDIO_PREFIX)
    fun toggleAudioBookmark(audioId: String): Boolean = toggleBookmark(audioId, BOOKMARKED_AUDIO_PREFIX)

    fun getAllBookmarkedItemIds(prefix: String): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(prefix) && prefs.getBoolean(it, false) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }
}

// CompositionLocal for UserPreferencesHelper for easier access in Composables if needed
// Not strictly necessary if only ViewModels access it, but can be handy.
val LocalUserPreferencesHelper: ProvidableCompositionLocal<UserPreferencesHelper?> = compositionLocalOf { null }
