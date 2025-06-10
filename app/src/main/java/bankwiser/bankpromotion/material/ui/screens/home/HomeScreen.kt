package bankwiser.bankpromotion.material.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.model.Category
import bankwiser.bankpromotion.material.ui.theme.*
import bankwiser.bankpromotion.material.ui.viewmodel.HomeViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@Composable
fun HomeScreen(onCategoryClick: (categoryId: String) -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(repository))
    val categories by viewModel.categories.collectAsState()

    Scaffold(
        topBar = { HomeHeader() }
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

@Composable
fun HomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(PrimaryIndigo, GradientEndPurple)
                )
            )
            .padding(20.dp)
    ) {
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
                    // The 'color' parameter is removed to let the theme apply the correct onSurface color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes, MCQs, & More",
                    style = MaterialTheme.typography.bodySmall
                    // The 'color' parameter is removed
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
        HomeScreen(onCategoryClick = {})
    }
}
