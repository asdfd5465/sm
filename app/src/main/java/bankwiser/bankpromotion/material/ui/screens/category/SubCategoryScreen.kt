package bankwiser.bankpromotion.material.ui.screens.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.model.SubCategory
import bankwiser.bankpromotion.material.ui.theme.TextPrimary
import bankwiser.bankpromotion.material.ui.theme.TextSecondary
import bankwiser.bankpromotion.material.ui.viewmodel.SavedStateViewModelFactory
import bankwiser.bankpromotion.material.ui.viewmodel.SubCategoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubCategoryScreen(
    onSubCategoryClick: (subCategoryId: String) -> Unit,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val viewModel: SubCategoryViewModel = viewModel(factory = SavedStateViewModelFactory(repository))
    val subCategories by viewModel.subCategories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Topic") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(subCategories) { subCategory ->
                TopicCard(subCategory = subCategory, onClick = { onSubCategoryClick(subCategory.id) })
            }
        }
    }
}

@Composable
fun TopicCard(subCategory: SubCategory, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = subCategory.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notes & MCQs Available",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
