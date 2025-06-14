package bankwiser.bankpromotion.material.ui.screens.topic

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
// import bankwiser.bankpromotion.material.billing.PREMIUM_SUBSCRIPTION_ID // Not needed directly here
import bankwiser.bankpromotion.material.data.model.*
import bankwiser.bankpromotion.material.player.PlayerManager
import bankwiser.bankpromotion.material.ui.theme.TextSecondary
import bankwiser.bankpromotion.material.ui.viewmodel.SavedStateViewModelFactory
import bankwiser.bankpromotion.material.ui.viewmodel.SubscriptionViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.TopicContentViewModel

enum class ContentTab { NOTES, FAQS, MCQS, AUDIO }

// Moved helper function to be accessible by SearchScreen as well
// CORRECTED Helper function to check content accessibility
fun isContentAccessible(isFreeLaunch: Boolean, isPremiumContent: Boolean, hasUserSubscribed: Boolean): Boolean {
    if (isFreeLaunch) { // Rule 1: Free launch content is always accessible
        return true
    }
    // If not free launch content, then check premium status
    return if (isPremiumContent) { // Rule 2a: If premium, user needs subscription
        hasUserSubscribed
    } else { // Rule 2b: If not premium (and not free launch), it's regular free content
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicContentScreen(
    onNoteClick: (noteId: String) -> Unit,
    onNavigateUp: () -> Unit,
    subCategoryName: String?
) {
    val context = LocalContext.current
    val application = context.applicationContext as BankWiserApplication
    val repository = application.contentRepository
    val topicViewModel: TopicContentViewModel = viewModel(factory = SavedStateViewModelFactory(repository, application))
    val subscriptionViewModel: SubscriptionViewModel = viewModel() 
    
    val uiState by topicViewModel.uiState.collectAsState()
    val hasPremiumAccess by subscriptionViewModel.hasPremiumAccess.collectAsState()
    // val premiumProductDetails by subscriptionViewModel.premiumProductDetails.collectAsState() // Collect for purchase flow

    var selectedTab by remember { mutableStateOf(ContentTab.NOTES) }
    val playerManager = remember { PlayerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.releasePlayer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subCategoryName ?: "Topic Content", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ContentTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.name) },
                        icon = {
                            when (tab) {
                                ContentTab.NOTES -> Icon(Icons.Filled.Description, contentDescription = "Notes")
                                ContentTab.FAQS -> Icon(Icons.Filled.HelpOutline, contentDescription = "FAQs")
                                ContentTab.MCQS -> Icon(Icons.Filled.Quiz, contentDescription = "MCQs")
                                ContentTab.AUDIO -> Icon(Icons.Filled.Audiotrack, contentDescription = "Audio")
                            }
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: ${uiState.error}") }
            } else {
                Crossfade(targetState = selectedTab, label = "content_type_tabs") { currentTab: ContentTab ->
                    when (currentTab) {
                        ContentTab.NOTES -> NotesList(uiState.notes, onNoteClick, hasPremiumAccess, subscriptionViewModel)
                        ContentTab.FAQS -> FaqsList(uiState.faqs, hasPremiumAccess, subscriptionViewModel)
                        ContentTab.MCQS -> McqsList(uiState.mcqs, hasPremiumAccess, subscriptionViewModel)
                        ContentTab.AUDIO -> AudioList(uiState.audioContent, playerManager, hasPremiumAccess, subscriptionViewModel)
                    }
                }
            }
        }
    }
}


@Composable
fun PremiumLockedOverlay(onClickAction: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize() // This will make the overlay fill its parent (the Card's Box) alpha for transparency
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) 
            .clickable(onClick = onClickAction),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Premium Content",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This is Premium Content",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Subscribe to BankWiser Pro to access.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClickAction) {
                Text("Unlock Premium")
            }
        }
    }
}


@Composable
fun NotesList(notes: List<Note>, onNoteClick: (String) -> Unit, hasPremiumAccess: Boolean, subscriptionViewModel: SubscriptionViewModel) {
    val context = LocalContext.current
    val userPrefsHelper = (context.applicationContext as BankWiserApplication).userPreferencesHelper
    if (notes.isEmpty()) { EmptyContentMessage("No notes available for this topic yet."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(notes) { note ->
            var isBookmarked by remember(note.id, userPrefsHelper.isNoteBookmarked(note.id)) {
                mutableStateOf(userPrefsHelper.isNoteBookmarked(note.id))
            }
            val accessible = isContentAccessible(note.isFreeLaunchContent, note.isPremium, hasPremiumAccess)
            val productDetails by subscriptionViewModel.premiumProductDetails.collectAsState()


            NoteItemCard(
                note = note,
                isBookmarked = isBookmarked,
                onClick = { 
                    if (accessible) onNoteClick(note.id) 
                    else subscriptionViewModel.launchPurchaseFlow(context as Activity, productDetails)
                },
                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleNoteBookmark(note.id) },
                isLocked = !accessible
            )
            // Removed the Box wrapper, click handling is now inside NoteItemCard
        }
    }
}

@Composable
fun FaqsList(faqs: List<Faq>, hasPremiumAccess: Boolean, subscriptionViewModel: SubscriptionViewModel) {
    val context = LocalContext.current
    val userPrefsHelper = (context.applicationContext as BankWiserApplication).userPreferencesHelper
    if (faqs.isEmpty()) { EmptyContentMessage("No FAQs available for this topic yet."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(faqs) { faq ->
            var isBookmarked by remember(faq.id, userPrefsHelper.isFaqBookmarked(faq.id)) {
                mutableStateOf(userPrefsHelper.isFaqBookmarked(faq.id))
            }
            val accessible = isContentAccessible(faq.isFreeLaunchContent, faq.isPremium, hasPremiumAccess)
            val productDetails by subscriptionViewModel.premiumProductDetails.collectAsState()
            FaqItem(
                faq = faq,
                isBookmarked = isBookmarked,
                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleFaqBookmark(faq.id) },
                isLocked = !accessible,
                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, productDetails) }
            )
        }
    }
}

@Composable
fun McqsList(mcqs: List<Mcq>, hasPremiumAccess: Boolean, subscriptionViewModel: SubscriptionViewModel) {
    val context = LocalContext.current
    val userPrefsHelper = (context.applicationContext as BankWiserApplication).userPreferencesHelper
    if (mcqs.isEmpty()) { EmptyContentMessage("No MCQs available for this topic yet."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(mcqs) { mcq ->
            var isBookmarked by remember(mcq.id, userPrefsHelper.isMcqBookmarked(mcq.id)) {
                mutableStateOf(userPrefsHelper.isMcqBookmarked(mcq.id))
            }
            val accessible = isContentAccessible(mcq.isFreeLaunchContent, mcq.isPremium, hasPremiumAccess)
            val productDetails by subscriptionViewModel.premiumProductDetails.collectAsState()
            McqItem(
                mcq = mcq,
                isBookmarked = isBookmarked,
                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleMcqBookmark(mcq.id) },
                isLocked = !accessible,
                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, productDetails) }
            )
        }
    }
}

@Composable
fun AudioList(audioItems: List<AudioContent>, playerManager: PlayerManager, hasPremiumAccess: Boolean, subscriptionViewModel: SubscriptionViewModel) {
    val context = LocalContext.current
    val userPrefsHelper = (context.applicationContext as BankWiserApplication).userPreferencesHelper
    if (audioItems.isEmpty()) { EmptyContentMessage("No audio content available for this topic yet."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(audioItems) { audio ->
            var isBookmarked by remember(audio.id, userPrefsHelper.isAudioBookmarked(audio.id)) {
                mutableStateOf(userPrefsHelper.isAudioBookmarked(audio.id))
            }
            val accessible = isContentAccessible(audio.isFreeLaunchContent, audio.isPremium, hasPremiumAccess)
            val productDetails by subscriptionViewModel.premiumProductDetails.collectAsState()
            AudioItemCard(
                audio = audio,
                playerManager = playerManager,
                isBookmarked = isBookmarked,
                onBookmarkToggle = { isBookmarked = userPrefsHelper.toggleAudioBookmark(audio.id) },
                isLocked = !accessible,
                onLockedItemClick = { subscriptionViewModel.launchPurchaseFlow(context as Activity, productDetails) }
            )
        }
    }
}

// ... (package, imports, isContentAccessible function, TopicContentScreen, Lists, etc. remain the same) ...
// --- Individual Item Composables with Lock Indicator ---

@Composable
fun NoteItemCard(
    note: Note,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isLocked: Boolean // This flag determines if the lock OVERLAY is shown and click is disabled
) {
    // Determine if the padlock icon itself should be shown (premium, not free launch)
    val showPadlockIcon = note.isPremium && !note.isFreeLaunchContent

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLocked, onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (showPadlockIcon && !isLocked) { // Show padlock if premium (not free launch) & accessible (meaning user is subbed)
                            Icon(
                                imageVector = Icons.Filled.LockOpen, // Or just Lock if you prefer
                                contentDescription = "Premium",
                                tint = MaterialTheme.colorScheme.primary, // Or a gold color
                                modifier = Modifier.size(16.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isLocked) TextSecondary.copy(alpha = 0.5f) else TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (!isLocked) {
                        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark Note",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else { // This is for the main big lock icon when content is actually locked
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(24.dp).padding(top = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        if (isLocked) {
            PremiumLockedOverlay(onClickAction = onClick)
        }
    }
}

@Composable
fun FaqItem(
    faq: Faq,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    isLocked: Boolean,
    onLockedItemClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val showPadlockIcon = faq.isPremium && !faq.isFreeLaunchContent

    Box {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .clickable(enabled = !isLocked) { if (!isLocked) expanded = !expanded else onLockedItemClick() }
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = faq.question,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else LocalContentColor.current
                        )
                        if (showPadlockIcon && !isLocked) {
                             Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Premium",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (!isLocked) {
                        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark FAQ",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.padding(start = 0.dp),
                            tint = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else LocalContentColor.current
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!isLocked) {
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            text = faq.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 0.dp, end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
        if (isLocked) {
            PremiumLockedOverlay(onClickAction = onLockedItemClick)
        }
    }
}


@Composable
fun McqItem(
    mcq: Mcq,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    isLocked: Boolean,
    onLockedItemClick: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    val showPadlockIcon = mcq.isPremium && !mcq.isFreeLaunchContent

    Box {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { // Changed to CenterVertically
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                         Text(
                            text = mcq.questionText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else LocalContentColor.current
                        )
                        if (showPadlockIcon && !isLocked) {
                             Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Premium",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    if (!isLocked) {
                        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark MCQ",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (!isLocked) {
                    val options = listOf("A" to mcq.optionA, "B" to mcq.optionB, "C" to mcq.optionC, "D" to mcq.optionD)
                    options.forEach { (prefix, text) ->
                        OptionRow(
                            prefix = prefix,
                            text = text,
                            isSelected = selectedOption == prefix,
                            showAsCorrect = showAnswer && prefix == mcq.correctOption,
                            showAsIncorrect = showAnswer && selectedOption == prefix && prefix != mcq.correctOption,
                            onSelected = { if (!showAnswer) selectedOption = prefix }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showAnswer = true },
                        enabled = selectedOption != null && !showAnswer,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (showAnswer) "Answer Shown" else "Check Answer")
                    }
                    if (showAnswer) {
                        Text(
                            text = "Correct Answer: ${mcq.correctOption}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                     Text("Subscribe to practice MCQs.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
        }
        if (isLocked) {
            PremiumLockedOverlay(onClickAction = onLockedItemClick)
        }
    }
}


@Composable
fun AudioItemCard(
    audio: AudioContent,
    playerManager: PlayerManager,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    isLocked: Boolean,
    onLockedItemClick: () -> Unit
) {
    val currentPlayerData by playerManager.playerState.collectAsState()
    val isThisAudioActive = currentPlayerData.currentPlayingUrl == audio.audioUrl
    val isPlayingThisAudio = isThisAudioActive && currentPlayerData.isActuallyPlaying
    val showPadlockIcon = audio.isPremium && !audio.isFreeLaunchContent

    Box {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(
                                audio.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                                color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else LocalContentColor.current
                            )
                            if (showPadlockIcon && !isLocked) {
                                 Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = "Premium",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp).padding(start = 4.dp)
                                )
                            }
                        }
                        audio.durationSeconds?.let {
                            Text(
                                "${it / 60}:${String.format("%02d", it % 60)}", style = MaterialTheme.typography.bodySmall,
                                color = if (isLocked) TextSecondary.copy(alpha = 0.5f) else TextSecondary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isLocked) {
                            IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmark Audio",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                // Click is now handled by the Box overlay if locked
                                if (isPlayingThisAudio) playerManager.pause()
                                else playerManager.play(audio.audioUrl)
                            }) {
                                Icon(
                                    imageVector = if (isPlayingThisAudio) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                    contentDescription = if (isPlayingThisAudio) "Pause" else "Play",
                                    modifier = Modifier.size(40.dp),
                                    tint = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(24.dp).padding(end = 4.dp), // Placeholder for play icon space
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                if (!isLocked && isThisAudioActive && currentPlayerData.error != null) {
                    Text(
                        text = "Error: ${currentPlayerData.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (isLocked) {
            PremiumLockedOverlay(onClickAction = onLockedItemClick)
        }
    }
}

@Composable
fun OptionRow(prefix: String, text: String, isSelected: Boolean, showAsCorrect: Boolean, showAsIncorrect: Boolean, onSelected: () -> Unit) {
    val backgroundColor = when {
        showAsCorrect -> Color.Green.copy(alpha = 0.2f)
        showAsIncorrect -> Color.Red.copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onSelected)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$prefix.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Text(text)
    }
}

@Composable
fun EmptyContentMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
