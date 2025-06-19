package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope // <<< IMPORT ADDED
import bankwiser.bankpromotion.material.data.local.THEME_SYSTEM
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch // <<< IMPORT ADDED

data class ProfileUiState(
    val user: FirebaseUser? = null,
    val themePreference: String = THEME_SYSTEM,
    val hasPremiumAccess: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesHelper = UserPreferencesHelper(application)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            user = auth.currentUser,
            themePreference = userPreferencesHelper.getThemePreference()
            // hasPremiumAccess will be updated by observing SubscriptionViewModel or similar
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Observe theme changes from UserPreferencesHelper
        viewModelScope.launch { // <<< launch coroutine
            userPreferencesHelper.themePreferenceFlow.collect { theme ->
                _uiState.update { it.copy(themePreference = theme) }
            }
        }

        // Observe user changes (e.g., sign out handled by AuthViewModel)
        // This listener will update the user in uiState when auth state changes
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            _uiState.update { it.copy(user = firebaseAuth.currentUser) }
        }
        auth.addAuthStateListener(authStateListener)

        // Make sure to remove the listener when the ViewModel is cleared
        viewModelScope.launch {
            // This is a bit of a workaround to keep the listener tied to viewModelScope
            // A cleaner way might be to manage the listener lifecycle with onCleared directly if possible
            // or use a callbackFlow if this becomes problematic.
            try {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation {
                        auth.removeAuthStateListener(authStateListener)
                    }
                }
            } catch (e: Exception) {
                // Coroutine cancelled
            }
        }
    }

    fun setThemePreference(themeValue: String) {
        userPreferencesHelper.setThemePreference(themeValue)
    }

    fun updateSubscriptionStatus(hasAccess: Boolean) {
        _uiState.update { it.copy(hasPremiumAccess = hasAccess) }
    }

    // Override onCleared to remove listeners if they weren't tied to viewModelScope cancellation
    // However, the above approach with suspendCancellableCoroutine should handle it.
    // override fun onCleared() {
    //     super.onCleared()
    //     // auth.removeAuthStateListener(authStateListener) // If listener wasn't scope-bound
    // }
}
