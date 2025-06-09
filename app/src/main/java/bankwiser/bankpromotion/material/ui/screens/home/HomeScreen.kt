package bankwiser.bankpromotion.material.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import bankwiser.bankpromotion.material.ui.theme.BankWiserProTheme
import bankwiser.bankpromotion.material.ui.viewmodel.HomeViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onCategoryClick: (categoryId: String) -> Unit) {
    val context = LocalContext.current
    val repository = ContentRepository((context.applicationContext as BankWiserApplication).contentDatabase)
    val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(repository))
    val categories by viewModel.categories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BankWiser Pro Categories") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 8.dp)) {
            items(categories) { category ->
                CategoryItem(category = category, onClick = { onCategoryClick(category.categoryId) })
            }
        }
    }
}

@Composable
fun CategoryItem(category: CategoryEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = category.categoryName ?: "Unnamed Category",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    BankWiserProTheme {
        HomeScreen(onCategoryClick = {})
    }
}
