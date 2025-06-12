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
import bankwiser.bankpromotion.material.player.PlayerManager
import bankwiser.bankpromotion.material.ui.screens.topic.AudioItemCard
import bankwiser.bankpromotion.material.ui.screens.topic.FaqItem
import bankwiser.bankpromotion.material.ui.screens.topic.McqItem
import bankwiser.bankpromotion.material.ui.screens.topic.NoteItemCard // Import the updated NoteItemCard
import bankwiser.bankpromotion.material.ui.viewmodel.SearchViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onNoteClick: (noteId: String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as BankWiserApplication
    val repository = application.contentRepository
    val userPrefsHelper = application.userPreferencesHelper // Get UserPreferencesHelper
    val viewModel: SearchViewModel = viewModel(factory = ViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(uiState.query) }

    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChanged(searchQuery)
    }

    val effectiveOnNavigateUp = if (uiState.initialScreen && uiState.query.isBlank()) {
        onNavigateUp // Allow normal back navigation if search is truly empty and initial
    } else if (uiState.query.isNotBlank()) {
        { searchQuery = "" } // Clear search query on back press if there's a query
    } else {
        onNavigateUp // Default back navigation
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
                    IconButton(onClick = effectiveOnNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoading && !uiState.initialScreen) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.noResults && !uiState.initialScreen) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No results found for \"${uiState.query}\"")
                }
            } else if (uiState.initialScreen && uiState.query.isBlank()){
                 Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Type to search content.")
                }
            }
            else {
                val playerManager = remember { PlayerManager(context) }
                DisposableEffect(Unit) { onDispose { playerManager.releasePlayer() } }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.notes.isNotEmpty()) {
                        item { SectionHeader("Notes") }
                        items(uiState.notes) { note ->
                            // Pass the required parameters to NoteItemCard
                            var isBookmarked by remember(note.id, userPrefsHelper.isNoteBookmarked(note.id)) {
                                mutableStateOf(userPrefsHelper.isNoteBookmarked(note.id))
                            }
                            var isRead by remember(note.id, userPrefsHelper.isNoteRead(note.id)) {
                                mutableStateOf(userPrefsHelper.isNoteRead(note.id))
                            }
                            NoteItemCard(
                                note = note,
                                isBookmarked = isBookmarked,
                                isRead = isRead,
                                onClick = { onNoteClick(note.id) },
                                onBookmarkToggle = {
                                    isBookmarked = userPrefsHelper.toggleNoteBookmark(note.id)
                                },
                                onReadToggle = {
                                    isRead = userPrefsHelper.toggleNoteReadStatus(note.id)
                                }
                            )
                        }
                    }
                    if (uiState.faqs.isNotEmpty()) {
                        item { SectionHeader("FAQs") }
                        items(uiState.faqs) { faq ->
                            FaqItem(faq = faq)
                            // TODO: Add bookmark/read for FAQs from search results later
                        }
                    }
                    if (uiState.mcqs.isNotEmpty()) {
                        item { SectionHeader("MCQs") }
                        items(uiState.mcqs) { mcq ->
                            McqItem(mcq = mcq)
                            // TODO: Add bookmark for MCQs from search results later
                        }
                    }
                    if (uiState.audioContent.isNotEmpty()) {
                        item { SectionHeader("Audio Content") }
                        items(uiState.audioContent) { audio ->
                            AudioItemCard(audio = audio, playerManager = playerManager)
                            // TODO: Add bookmark for Audio from search results later
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
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}
