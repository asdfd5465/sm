package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.flow.*

class NoteDetailViewModel(private val repository: ContentRepository, private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val noteId: StateFlow<String> = savedStateHandle.getStateFlow("noteId", "")

    val note: StateFlow<NoteEntity?> = noteId.flatMapLatest { id ->
        repository.getNote(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}
