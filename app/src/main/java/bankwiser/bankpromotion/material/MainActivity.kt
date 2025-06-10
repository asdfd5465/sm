package bankwiser.bankpromotion.material

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import bankwiser.bankpromotion.material.ui.navigation.AppNavigation
import bankwiser.bankpromotion.material.ui.theme.BankWiserProTheme
import bankwiser.bankpromotion.material.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            credentialManager = CredentialManager.create(applicationContext)
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

                    // Error message display
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
                                val signInResult = googleAuthUiClient.signIn(serverClientId)
                                authViewModel.onSignInResult(signInResult)
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
