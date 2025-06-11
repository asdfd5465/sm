package bankwiser.bankpromotion.material.auth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth

    private val _authState = MutableStateFlow(AuthState(user = auth.currentUser))
    val authState: StateFlow<AuthState> = _authState

    fun signInWithGoogle(googleSignInClient: GoogleSignInClient, launcher: ActivityResultLauncher<Intent>) {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    fun handleGoogleSignInResult(intent: Intent?) {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                auth.signInWithCredential(credential).await()
                _authState.value = AuthState(user = auth.currentUser, isLoading = false)
            } catch (e: Exception) {
                _authState.value = AuthState(isLoading = false, error = e.localizedMessage ?: "Sign-in failed")
            }
        }
    }

    fun signOut(googleSignInClient: GoogleSignInClient) {
        viewModelScope.launch {
            try {
                auth.signOut()
                googleSignInClient.signOut().await() // Also sign out from Google
                _authState.value = AuthState(user = null, isLoading = false, error = null)
            } catch (e: Exception) {
                // Handle potential errors during sign out if necessary
                _authState.value = _authState.value.copy(isLoading = false, error = e.localizedMessage ?: "Sign-out failed")
            }
        }
    }
}
