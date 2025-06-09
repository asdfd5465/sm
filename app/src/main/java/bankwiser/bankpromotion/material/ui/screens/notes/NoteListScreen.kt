package bankwiser.bankpromotion.material.ui.screens.notes

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.ui.viewmodel.NoteListViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.SavedStateViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onNoteClick: (noteId: String) -> Unit,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as BankWiserApplication).contentRepository
    val viewModel: NoteListViewModel = viewModel(
        factory = SavedStateViewModelFactory(
            owner = LocalSavedStateRegistryOwner.current,
            repository = repository
        )
    )
    val notes by viewModel.notes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 8.dp)) {
            items(notes) { note ->
                NoteItem(note = note, onClick = { onNoteClick(note.noteId) })
            }
        }
    }
}

@Composable
fun NoteItem(note: NoteEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title ?: "No Title",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.body ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
