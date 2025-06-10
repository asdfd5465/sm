package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.SignInResult
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel: ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val user = auth.currentUser
        _authState.update { it.copy(isLoggedIn = user != null) }
    }

    fun onSignInResult(result: SignInResult) {
        if(result.data != null) {
            firebaseSignInWithGoogle(result.data.idToken)
        } else {
            _authState.update { it.copy(error = result.errorMessage, isLoading = false)}
        }
    }

    private fun firebaseSignInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true) }
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                _authState.update { it.copy(isLoggedIn = true, isLoading = false, error = null) }
            } catch (e: Exception) {
                e.printStackTrace()
                _authState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun resetAuthState() {
        _authState.update { AuthState() } // Resets to initial state (logged out)
    }
}
