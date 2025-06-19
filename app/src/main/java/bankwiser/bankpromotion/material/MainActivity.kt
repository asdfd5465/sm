package bankwiser.bankpromotion.material

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import bankwiser.bankpromotion.material.data.local.THEME_DARK
import bankwiser.bankpromotion.material.data.local.THEME_LIGHT
import bankwiser.bankpromotion.material.data.local.THEME_SYSTEM
import bankwiser.bankpromotion.material.ui.navigation.AppNavigation
import bankwiser.bankpromotion.material.ui.theme.BankWiserProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val application = LocalContext.current.applicationContext as BankWiserApplication
            val userPreferencesHelper = application.userPreferencesHelper
            val themePreference by userPreferencesHelper.themePreferenceFlow.collectAsState()

            val useDarkTheme = when (themePreference) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme() // THEME_SYSTEM
            }

            BankWiserProTheme(darkTheme = useDarkTheme) { // Pass dynamic theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
