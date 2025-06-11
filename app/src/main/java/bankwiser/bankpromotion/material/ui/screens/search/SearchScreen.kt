package bankwiser.bankpromotion.material.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.ui.screens.topic.FaqItem // Re-use FaqItem
import bankwiser.bankpromotion.material.ui.screens.topic.NoteItemCard // Re-use NoteItemCard
import bankwiser.bankpromotion.material.ui.viewmodel.SearchViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onNoteClick: (noteId: String) -> Unit
    // Add onFaqClick, onMcqClick, onAudioClick later if needed
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val viewModel: SearchViewModel = viewModel(factory = ViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChanged(searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search content...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, "Search Icon")},
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.noResults && uiState.query.isNotBlank()) {
                 Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No results found for \"${uiState.query}\"")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.notes.isNotEmpty()) {
                        item { SectionHeader("Notes") }
                        items(uiState.notes) { note ->
                            NoteItemCard(note = note, onClick = { onNoteClick(note.id) })
                        }
                    }
                    if (uiState.faqs.isNotEmpty()) {
                        item { SectionHeader("FAQs") }
                        items(uiState.faqs) { faq ->
                            FaqItem(faq = faq) // No click action for FAQ item itself for now
                        }
                    }
                    // Add sections for MCQs and Audio later
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
