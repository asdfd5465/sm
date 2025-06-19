package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.local.THEME_DARK
import bankwiser.bankpromotion.material.data.local.THEME_LIGHT
import bankwiser.bankpromotion.material.data.local.THEME_SYSTEM
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*

data class ProfileUiState(
    val user: FirebaseUser? = null,
    val themePreference: String = THEME_SYSTEM,
    val hasPremiumAccess: Boolean = false // This will come from SubscriptionViewModel or a shared source
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesHelper = UserPreferencesHelper(application)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // This will be combined with SubscriptionViewModel's state later for premium access
    private val _uiState = MutableStateFlow(
        ProfileUiState(
            user = auth.currentUser,
            themePreference = userPreferencesHelper.getThemePreference()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Observe theme changes from UserPreferencesHelper
        viewModelScope.launch {
            userPreferencesHelper.themePreferenceFlow.collect { theme ->
                _uiState.update { it.copy(themePreference = theme) }
            }
        }
        // Observe user changes (e.g., sign out handled by AuthViewModel)
        auth.addAuthStateListener { firebaseAuth ->
            _uiState.update { it.copy(user = firebaseAuth.currentUser) }
        }
    }

    fun setThemePreference(themeValue: String) {
        userPreferencesHelper.setThemePreference(themeValue)
        // _uiState will update automatically via the themePreferenceFlow collection
    }

    // This would be updated by observing SubscriptionViewModel's hasPremiumAccess
    // For now, this is a placeholder if you directly control it from ProfileScreen for testing
    fun updateSubscriptionStatus(hasAccess: Boolean) {
        _uiState.update { it.copy(hasPremiumAccess = hasAccess) }
    }
}
