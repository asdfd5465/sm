package bankwiser.bankpromotion.material

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import bankwiser.bankpromotion.material.ui.navigation.AppNavigation
import bankwiser.bankpromotion.material.ui.theme.BankWiserProTheme
import bankwiser.bankpromotion.material.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BankWiserProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartIntentSenderForResult(),
                        onResult = { result ->
                            if (result.resultCode == RESULT_OK) {
                                lifecycleScope.launch {
                                    val signInResult = googleAuthUiClient.signInWithIntent(
                                        intent = result.data ?: return@launch
                                    )
                                    authViewModel.onSignInResult(signInResult)
                                }
                            }
                        }
                    )

                    LaunchedEffect(key1 = authViewModel.authState.value.error) {
                        authViewModel.authState.value.error?.let { error ->
                            Toast.makeText(applicationContext, error, Toast.LENGTH_LONG).show()
                        }
                    }

                    AppNavigation(
                        navController = navController,
                        authViewModel = authViewModel,
                        onSignInClick = {
                            lifecycleScope.launch {
                                val serverClientId = getString(R.string.default_web_client_id)
                                // Add a log to prove the ID is being read
                                Log.d("AUTH_DEBUG", "Attempting sign-in with client ID: $serverClientId")

                                val signInIntentSender = googleAuthUiClient.signIn(serverClientId)

                                if (signInIntentSender == null) {
                                    Log.e("AUTH_DEBUG", "signInIntentSender is null. Check SHA-1 and Client ID in Firebase.")
                                    Toast.makeText(
                                        applicationContext,
                                        "Could not start sign-in. Check Web Client ID.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }
                                launcher.launch(
                                    IntentSenderRequest.Builder(
                                        signInIntentSender
                                    ).build()
                                )
                            }
                        },
                        onSignOutClick = {
                            lifecycleScope.launch {
                                googleAuthUiClient.signOut()
                                authViewModel.resetAuthState()
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
