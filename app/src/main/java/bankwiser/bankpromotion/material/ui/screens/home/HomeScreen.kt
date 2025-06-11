package bankwiser.bankpromotion.material.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color // <<< IMPORT ADDED HERE
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
import bankwiser.bankpromotion.material.data.model.Category
import bankwiser.bankpromotion.material.ui.theme.*
import bankwiser.bankpromotion.material.ui.viewmodel.HomeViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCategoryClick: (categoryId: String) -> Unit,
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory(repository))
    val categories by homeViewModel.categories.collectAsState()

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(stringResource(id = R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    Scaffold(
        topBar = {
            HomeHeader(onSignOutClick = {
                authViewModel.signOut(googleSignInClient)
                onSignOut()
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories) { category ->
                CategoryCard(category = category, onClick = { onCategoryClick(category.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeader(onSignOutClick: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Welcome back! 👋",
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
            containerColor = Color.Transparent, // Make TopAppBar transparent to show gradient
            titleContentColor = TextOnPrimary,
            actionIconContentColor = TextOnPrimary
        ),
        actions = {
            IconButton(onClick = onSignOutClick) {
                Icon(
                    imageVector = Icons.Filled.ExitToApp,
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
        HomeScreen(onCategoryClick = {}, onSignOut = {})
    }
}
