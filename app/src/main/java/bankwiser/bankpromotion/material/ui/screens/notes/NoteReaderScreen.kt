package bankwiser.bankpromotion.material.ui.screens.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import bankwiser.bankpromotion.material.ui.viewmodel.NoteDetailViewModel
import bankwiser.bankpromotion.material.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReaderScreen(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val repository = ContentRepository((context.applicationContext as BankWiserApplication).contentDatabase)
    val viewModel: NoteDetailViewModel = viewModel(factory = ViewModelFactory(repository))
    val note by viewModel.note.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.title ?: "Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            note?.let {
                Text(text = it.title, style = MaterialTheme.typography.headlineSmall)
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text(text = it.body, style = MaterialTheme.typography.bodyLarge)
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.wrapContentSize())
            }
        }
    }
}
