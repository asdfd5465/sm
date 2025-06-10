package bankwiser.bankpromotion.material

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

data class SignInResult(
    val data: UserData?,
    val errorMessage: String?
)

data class UserData(
    val userId: String,
    val username: String?,
    val profilePictureUrl: String?,
    val idToken: String
)

class GoogleAuthUiClient(
    private val context: Context,
    private val oneTapClient: SignInClient
) {
    private val auth = FirebaseAuth.getInstance()

    // The serverClientId is now passed directly to this function
    suspend fun signIn(serverClientId: String): IntentSender? {
        val result = try {
            oneTapClient.beginSignIn(
                buildSignInRequest(serverClientId)
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
            null
        }
        return result?.pendingIntent?.intentSender
    }

    suspend fun signInWithIntent(intent: Intent): SignInResult {
        val credential = oneTapClient.getSignInCredentialFromIntent(intent)
        val idToken = credential.googleIdToken
        if (idToken == null) {
            return SignInResult(
                data = null,
                errorMessage = "No ID token!"
            )
        }

        return SignInResult(
            data = UserData(
                userId = credential.id,
                username = credential.displayName,
                profilePictureUrl = credential.profilePictureUri?.toString(),
                idToken = idToken
            ),
            errorMessage = null
        )
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            oneTapClient.signOut().await()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
        }
    }

    // The serverClientId is now required by this function
    private fun buildSignInRequest(serverClientId: String): BeginSignInRequest {
        return BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(serverClientId) // Use the direct string value
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()
    }
}
