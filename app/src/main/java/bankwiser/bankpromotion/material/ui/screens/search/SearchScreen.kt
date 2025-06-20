package bankwiser.bankpromotion.material.ui.screens.search

import android.app.Activity
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
import androidx.lifecycle.ViewModel // Import ViewModel
import androidx.lifecycle.ViewModelProvider // Import ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.player.PlayerManager
import bankwiser.bankpromotion.material.ui.screens.topic.AudioItemCard
import bankwiser.bankpromotion.material.ui.screens.topic.FaqItem
import bankwiser.bankpromotion.material.ui.screens.topic.McqItem
import bankwiser.bankpromotion.material.ui.screens.topic.NoteItemCard
import bankwiser.bankpromotion.material.ui.screens.topic.isContentAccessible
import bankwiser.bankpromotion.material.ui.viewmodel.DownloadViewModel // Import DownloadViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.SearchViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.SubscriptionViewModel
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
    val userPrefsHelper = application.userPreferencesHelper
    val searchViewModel: SearchViewModel = viewModel(factory = ViewModelFactory(repository, application))
    val subscriptionViewModel: SubscriptionViewModel = viewModel()

    val uiState by searchViewModel.uiState.collectAsState()
    val hasPremiumAccess by subscriptionViewModel.hasPremiumAccess.collectAsState()
    var searchQuery by remember { mutableStateOf(uiState.query) }

    LaunchedEffect(searchQuery) {
        searchViewModel.onSearchQueryChanged(searchQuery)
    }

    val effectiveOnNavigateUp = if (uiState.initialScreen && uiState.query.isBlank()) {
        onNavigateUp
    } else if (uiState.query.isNotBlank()) {
        { searchQuery = "" }
    } else {
        onNavigateUp
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
                },
                 colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoading && !uiState.initialScreen) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.noResults && !uiState.initialScreen) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("No results found for \"${uiState.query}\"") }
            } else if (uiState.initialScreen && uiState.query.isBlank()){
                 Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("Type to search content.") }
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
                            var isBookmarked by remember(note.id, userPrefsHelper.isNoteBookmarked(note.id)) {
                                mutableStateOf(userPrefsHelper.isNoteBookmarked(note.id))
                            }
                            val accessible = isContentAccessible(note.isFreeLaunchContent, note.isPremium, hasPremiumAccess)
                            NoteItemCard(
                                note = note,
                                isBookmarked = isBookmarked,
                                onClick = {
                                    if (accessible) onNoteClick(note.id)
                                    else subscriptionViewModel.launchPurchaseFlow(context as Activity, subscriptionViewModel.premiumProductDetails.value)
                                },
                                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleNoteBookmark(note.id) },
                                isLocked = !accessible
                            )
                        }
                    }
                    if (uiState.faqs.isNotEmpty()) {
                        item { SectionHeader("FAQs") }
                        items(uiState.faqs) { faq ->
                            var isBookmarked by remember(faq.id, userPrefsHelper.isFaqBookmarked(faq.id)) {
                                mutableStateOf(userPrefsHelper.isFaqBookmarked(faq.id))
                            }
                             val accessible = isContentAccessible(faq.isFreeLaunchContent, faq.isPremium, hasPremiumAccess)
                            FaqItem(
                                faq = faq,
                                isBookmarked = isBookmarked,
                                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleFaqBookmark(faq.id) },
                                isLocked = !accessible,
                                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, subscriptionViewModel.premiumProductDetails.value) }
                            )
                        }
                    }
                    if (uiState.mcqs.isNotEmpty()) {
                        item { SectionHeader("MCQs") }
                        items(uiState.mcqs) { mcq ->
                            var isBookmarked by remember(mcq.id, userPrefsHelper.isMcqBookmarked(mcq.id)) {
                                mutableStateOf(userPrefsHelper.isMcqBookmarked(mcq.id))
                            }
                            val accessible = isContentAccessible(mcq.isFreeLaunchContent, mcq.isPremium, hasPremiumAccess)
                            McqItem(
                                mcq = mcq,
                                isBookmarked = isBookmarked,
                                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleMcqBookmark(mcq.id) },
                                isLocked = !accessible,
                                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, subscriptionViewModel.premiumProductDetails.value) }
                            )
                        }
                    }
                    if (uiState.audioContent.isNotEmpty()) {
                        item { SectionHeader("Audio Content") }
                        items(uiState.audioContent) { audio ->
                            var isBookmarked by remember(audio.id, userPrefsHelper.isAudioBookmarked(audio.id)) {
                                mutableStateOf(userPrefsHelper.isAudioBookmarked(audio.id))
                            }
                            val accessible = isContentAccessible(audio.isFreeLaunchContent, audio.isPremium, hasPremiumAccess)
                            // Create DownloadViewModel for each AudioItemCard in search results
                            val downloadViewModel: DownloadViewModel = viewModel(
                                key = "search_${audio.id}", // Unique key for search results
                                factory = object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        if (modelClass.isAssignableFrom(DownloadViewModel::class.java)) {
                                            @Suppress("UNCHECKED_CAST")
                                            return DownloadViewModel(application) as T
                                        }
                                        throw IllegalArgumentException("Unknown ViewModel class in SearchScreen for DownloadViewModel")
                                    }
                                }
                            )
                            AudioItemCard(
                                audio = audio,
                                playerManager = playerManager,
                                isBookmarked = isBookmarked,
                                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleAudioBookmark(audio.id) },
                                isLocked = !accessible,
                                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, subscriptionViewModel.premiumProductDetails.value) },
                                downloadViewModel = downloadViewModel // Pass the DownloadViewModel
                            )
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
