package bankwiser.bankpromotion.material.ui.screens.home

import android.app.Activity // Required for casting context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.R
import bankwiser.bankpromotion.material.auth.AuthViewModel
import bankwiser.bankpromotion.material.data.UpdateState // Import your UpdateState
import bankwiser.bankpromotion.material.data.model.Category
import bankwiser.bankpromotion.material.ui.theme.*
import bankwiser.bankpromotion.material.ui.viewmodel.HomeViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.SubscriptionViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseUser
import android.util.Log // For logging in AssetUpdateUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCategoryClick: (categoryId: String) -> Unit,
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    subscriptionViewModel: SubscriptionViewModel = viewModel()
) {
    val context = LocalContext.current
    val application = context.applicationContext as BankWiserApplication
    val repository = application.contentRepository
    val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory(repository))
    val categories by homeViewModel.categories.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val hasPremiumAccess by subscriptionViewModel.hasPremiumAccess.collectAsState()

    // For Asset Pack Updates
    val assetPackUpdateManager = application.assetPackUpdateManager
    val updateState by assetPackUpdateManager.updateState.collectAsState()

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(stringResource(id = R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    Scaffold(
        topBar = {
            HomeHeader(
                user = authState.user,
                onSignOutClick = {
                    authViewModel.signOut(googleSignInClient)
                    onSignOut()
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) { // Changed from Box to Column
            // --- Asset Pack Update UI ---
            AssetUpdateUI(
                updateState = updateState,
                onDownloadConfirm = { assetPackUpdateManager.startUpdateProcess() },
                onDismiss = {
                    assetPackUpdateManager.userDismissedPrompt()
                }
            )

            // --- Temporary Subscription Toggle Button ---
            Button(
                onClick = {
                    val currentSimulatedStatus = application.userPreferencesHelper.isUserSubscribed()
                    subscriptionViewModel.setSimulatedSubscription(!currentSimulatedStatus)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (hasPremiumAccess) "Simulate Unsubscribe" else "Simulate Subscribe")
            }

            // --- Categories List ---
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories) { category ->
                    CategoryCard(category = category, onClick = { onCategoryClick(category.id) })
                }
            }
        }
    }
}

// New Composable for Update UI
@Composable
fun AssetUpdateUI(
    updateState: UpdateState,
    onDownloadConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    when (updateState) {
        is UpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Content Update Available") },
                text = { Text("A new version of the content (v${updateState.remoteVersion}) is available. Download now?") },
                confirmButton = {
                    TextButton(onClick = onDownloadConfirm) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Later") }
                }
            )
        }
        is UpdateState.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Downloading update for ${updateState.packName}...")
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { updateState.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${updateState.progress}%")
            }
        }
        is UpdateState.DownloadFailed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Download Failed") },
                text = { Text("Failed to download content update for ${updateState.packName}. Error code: ${updateState.errorCode}. Please try again later.") },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
            )
        }
         is UpdateState.InstallingUpdate -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Installing update...")
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
        is UpdateState.UpdateComplete -> {
            // Consider a Snackbar or Toast for a brief period.
            // This state is transient; the UI will likely recompose due to data refresh.
            Log.i("AssetUpdateUI", "Update to version ${updateState.newVersion} complete.")
            // Here you might trigger a re-fetch of data for ViewModels if needed
            // or rely on app restart/re-navigation to pick up the new DB.
        }
        is UpdateState.UpdateFailedInstallation -> {
             AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Update Failed") },
                text = { Text("Failed to install the content update. Please ensure you have enough storage space and try again.") },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
            )
        }
        is UpdateState.RemoteConfigFetchFailed -> {
            Log.w("AssetUpdateUI", "Failed to check for updates (Remote Config).")
            // Optionally show a non-intrusive message like a Snackbar
        }
        UpdateState.CheckingForUpdate, UpdateState.Idle, UpdateState.NoUpdateNeeded, UpdateState.VerifyingUpdate -> {
            // No specific prominent UI for these states for now.
            // A subtle "Checking for updates..." text could be added if desired.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeader(user: FirebaseUser?, onSignOutClick: () -> Unit) {
    val userName = user?.displayName?.substringBefore(" ") ?: "User"

    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Welcome back, $userName! 👋",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextOnPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ready to ace your promotion?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextOnPrimary.copy(alpha = 0.9f)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(PrimaryIndigo, GradientEndPurple)
                )
            ),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = TextOnPrimary,
            actionIconContentColor = TextOnPrimary
        ),
        actions = {
            IconButton(onClick = onSignOutClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp, // Corrected Icon
                    contentDescription = "Sign Out",
                    tint = TextOnPrimary
                )
            }
        }
    )
}

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getIconForCategory(category.id),
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes, MCQs, & More",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun getIconForCategory(id: String): String {
    return when {
        id.contains("RBI") -> "🏦"
        id.contains("PSB_POLICY") -> "🏢"
        id.contains("SOCIO") -> "🌍"
        id.contains("CIRCULAR") -> "📜"
        else -> "📚"
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BankWiserProTheme {
        HomeScreen(onCategoryClick = {}, onSignOut = {}, authViewModel = AuthViewModel())
    }
}
