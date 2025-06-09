package bankwiser.bankpromotion.material.ui.screens.category

import android.app.Application
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import bankwiser.bankpromotion.material.ui.viewmodel.SavedStateViewModelFactory
import bankwiser.bankpromotion.material.ui.viewmodel.SubCategoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubCategoryScreen(
    onSubCategoryClick: (subCategoryId: String) -> Unit,
    onNavigateUp: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: SubCategoryViewModel = viewModel(
        factory = SavedStateViewModelFactory(
            owner = LocalSavedStateRegistryOwner.current,
            application = application
        )
    )
    val subCategories by viewModel.subCategories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Topic") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 8.dp)) {
            items(subCategories) { subCategory ->
                SubCategoryItem(subCategory = subCategory, onClick = { onSubCategoryClick(subCategory.subCategoryId) })
            }
        }
    }
}

@Composable
fun SubCategoryItem(subCategory: SubCategoryEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = subCategory.subCategoryName ?: "Unnamed Topic",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
