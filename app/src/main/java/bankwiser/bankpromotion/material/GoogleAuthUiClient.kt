package bankwiser.bankpromotion.material

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth

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
    private val credentialManager: CredentialManager
) {
    private val auth = FirebaseAuth.getInstance()

    suspend fun signIn(serverClientId: String): SignInResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            handleSignIn(result)
        } catch (e: Exception) {
            e.printStackTrace()
            SignInResult(data = null, errorMessage = e.message)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleSignIn(result: GetCredentialResponse): SignInResult {
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            return SignInResult(
                data = UserData(
                    userId = credential.id,
                    username = credential.displayName,
                    profilePictureUrl = credential.profilePictureUri?.toString(),
                    idToken = credential.idToken
                ),
                errorMessage = null
            )
        }
        // Handle other types if necessary, like Passkeys
        if (credential is CustomCredential && credential.type == "androidx.credentials.TYPE_PASSWORD_CREDENTIAL") {
            return SignInResult(data = null, errorMessage = "Password credential not supported.")
        }

        return SignInResult(data = null, errorMessage = "Unrecognized credential type.")
    }
}
