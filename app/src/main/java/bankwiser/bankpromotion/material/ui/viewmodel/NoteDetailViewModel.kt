package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.model.Note
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteDetailViewModel(private val repository: ContentRepository, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val noteId: String = savedStateHandle.get<String>("noteId")!!

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    init {
        loadNote()
    }

    private fun loadNote() {
        viewModelScope.launch {
            _note.value = withContext(Dispatchers.IO) {
                repository.getNote(noteId)
            }
        }
    }
}
