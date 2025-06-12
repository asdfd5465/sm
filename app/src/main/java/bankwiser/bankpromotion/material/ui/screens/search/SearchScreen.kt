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
import bankwiser.bankpromotion.material.player.PlayerManager // Assuming PlayerManager is needed for AudioItemCard
import bankwiser.bankpromotion.material.ui.screens.topic.AudioItemCard // Re-use AudioItemCard
import bankwiser.bankpromotion.material.ui.screens.topic.FaqItem
import bankwiser.bankpromotion.material.ui.screens.topic.McqItem // Re-use McqItem
import bankwiser.bankpromotion.material.ui.screens.topic.NoteItemCard
import bankwiser.bankpromotion.material.ui.viewmodel.SearchViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onNoteClick: (noteId: String) -> Unit,
    // Add onMcqClick and onAudioClick if needed for direct navigation from search
    // For now, clicking these items from search might not do anything further than display
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val viewModel: SearchViewModel = viewModel(factory = ViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(uiState.query) } // Initialize with VM state

    // This will trigger search when searchQuery state changes
    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChanged(searchQuery)
    }
     // To handle back press correctly when search is empty.
    val effectiveOnNavigateUp = if (uiState.initialScreen) {
        { /* If initial screen (no query yet), let system handle back or specific nav controller pop */ }
    } else {
        onNavigateUp // Use the provided onNavigateUp for other cases
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
                        leadingIcon = { Icon(Icons.Filled.Search, "Search Icon") },
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
                    IconButton(onClick = effectiveOnNavigateUp) { // Use effectiveOnNavigateUp
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoading && !uiState.initialScreen) { // Show loader only if not initial and loading
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.noResults && !uiState.initialScreen) { // Show no results only if not initial and no results
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No results found for \"${uiState.query}\"")
                }
            } else if (uiState.initialScreen && uiState.query.isBlank()){
                 Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Type to search content.")
                }
            }
            else {
                // PlayerManager instance needed for AudioItemCard if it's displayed
                val playerManager = remember { PlayerManager(context) }
                DisposableEffect(Unit) { onDispose { playerManager.releasePlayer() } }

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
                            FaqItem(faq = faq)
                        }
                    }
                    if (uiState.mcqs.isNotEmpty()) {
                        item { SectionHeader("MCQs") }
                        items(uiState.mcqs) { mcq ->
                            McqItem(mcq = mcq)
                        }
                    }
                    if (uiState.audioContent.isNotEmpty()) {
                        item { SectionHeader("Audio Content") }
                        items(uiState.audioContent) { audio ->
                            AudioItemCard(audio = audio, playerManager = playerManager)
                        }
                    }
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
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp) // Added more spacing
    )
}
