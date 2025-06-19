package bankwiser.bankpromotion.material.ui.screens.profile

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.R
import bankwiser.bankpromotion.material.auth.AuthViewModel
import bankwiser.bankpromotion.material.billing.PREMIUM_SUBSCRIPTION_ID
import bankwiser.bankpromotion.material.data.local.THEME_DARK
import bankwiser.bankpromotion.material.data.local.THEME_LIGHT
import bankwiser.bankpromotion.material.data.local.THEME_SYSTEM
import bankwiser.bankpromotion.material.ui.viewmodel.ProfileViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.SubscriptionViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = viewModel(), // For sign out
    subscriptionViewModel: SubscriptionViewModel = viewModel(), // For subscription status & purchase
    onNavigateToLogin: () -> Unit,
    onManageDownloads: () -> Unit, // Placeholder for future
    onHelpAndFeedback: () -> Unit, // Placeholder
    onRateApp: () -> Unit, // Placeholder
    onTermsAndPrivacy: () -> Unit // Placeholder
) {
    val context = LocalContext.current
    val application = context.applicationContext as BankWiserApplication
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ViewModelFactory(application.contentRepository, application) // Pass application
    )
    val profileUiState by profileViewModel.uiState.collectAsState()
    val authState by authViewModel.authState.collectAsState() // To ensure recomposition on sign out
    val hasPremiumAccess by subscriptionViewModel.hasPremiumAccess.collectAsState()
    val premiumProductDetails by subscriptionViewModel.premiumProductDetails.collectAsState()


    // For Sign Out
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    var showThemeDialog by remember { mutableStateOf(false) }

    // Update ProfileViewModel's subscription status (could also be done via a shared service/repo)
    LaunchedEffect(hasPremiumAccess) {
        profileViewModel.updateSubscriptionStatus(hasPremiumAccess)
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // User Info Section
            profileUiState.user?.let { firebaseUser ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Placeholder for Avatar - from mockup: A in a circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firebaseUser.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = firebaseUser.displayName ?: "User Name",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = firebaseUser.email ?: "user.email@example.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (profileUiState.hasPremiumAccess) {
                            Text(
                                "Premium Member ✨",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981) // Greenish from mockup
                            )
                        } else {
                             Text(
                                "Free Tier",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            // Settings Items
            ProfileListItem(
                icon = Icons.Filled.Brightness6, // Or DarkMode / LightMode based on current
                text = "App Theme: ${profileUiState.themePreference.replaceFirstChar { it.uppercase() }}",
                onClick = { showThemeDialog = true }
            )
            // TODO: Notification Settings - Later Phase
            // ProfileListItem(icon = Icons.Filled.Notifications, text = "Notification Settings", onClick = { /* TODO */ })

            HorizontalDivider()

            ProfileListItem(
                icon = Icons.Filled.Diamond, // Premium icon
                text = if (profileUiState.hasPremiumAccess) "Manage Subscription" else "Upgrade to Premium",
                onClick = {
                    // If not subscribed, launch purchase flow. If subscribed, go to Play Store manage subs.
                    if (!profileUiState.hasPremiumAccess) {
                        premiumProductDetails?.let {
                             subscriptionViewModel.launchPurchaseFlow(context as Activity, it)
                        }
                    } else {
                        // TODO: Link to Google Play manage subscriptions page
                        // val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                        // context.startActivity(intent)
                         println("TODO: Navigate to Manage Subscriptions in Play Store")
                    }
                }
            )
            ProfileListItem(icon = Icons.Filled.Download, text = "Manage Downloads", onClick = onManageDownloads)

            HorizontalDivider()

            ProfileListItem(icon = Icons.Filled.HelpOutline, text = "Help & Feedback", onClick = onHelpAndFeedback)
            ProfileListItem(icon = Icons.Filled.StarRate, text = "Rate BankWiser Pro", onClick = onRateApp)
            ProfileListItem(icon = Icons.Filled.Article, text = "Terms & Privacy", onClick = onTermsAndPrivacy)

            HorizontalDivider()

            ProfileListItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                text = "Logout",
                onClick = {
                    authViewModel.signOut(googleSignInClient)
                    // Navigation to login screen is handled by AppNavigation's LaunchedEffect on authState
                },
                isDestructive = true
            )

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "App Version 1.0.0 (Build ...)", // TODO: Get from BuildConfig
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = profileUiState.themePreference,
            onThemeSelected = { themeValue ->
                profileViewModel.setThemePreference(themeValue)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun ProfileListItem(icon: ImageVector, text: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp), // Consistent padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!isDestructive) { // Don't show arrow for destructive actions like logout
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(THEME_LIGHT to "Light", THEME_DARK to "Dark", THEME_SYSTEM to "System Default")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                themes.forEach { (themeValue, themeName) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(themeValue) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == themeValue,
                            onClick = { onThemeSelected(themeValue) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(themeName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
