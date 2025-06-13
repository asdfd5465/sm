package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import bankwiser.bankpromotion.material.data.model.Note
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NoteDetailUiState(
    val note: Note? = null,
    val isBookmarked: Boolean = false,
    // val isRead: Boolean = false, // Removed
    val isLoading: Boolean = true,
    val error: String? = null
)

class NoteDetailViewModel(
    private val repository: ContentRepository,
    private val userPreferencesHelper: UserPreferencesHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = savedStateHandle.get<String>("noteId")!!

    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        loadNoteDetails()
    }

    private fun loadNoteDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val noteData = withContext(Dispatchers.IO) {
                    repository.getNote(noteId)
                }
                _uiState.update {
                    it.copy(
                        note = noteData,
                        isBookmarked = userPreferencesHelper.isNoteBookmarked(noteId),
                        // isRead = userPreferencesHelper.isNoteRead(noteId), // Removed
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleBookmark() {
        val newBookmarkStatus = userPreferencesHelper.toggleNoteBookmark(noteId)
        _uiState.update { it.copy(isBookmarked = newBookmarkStatus) }
    }

    // fun toggleReadStatus() { // Removed
    //     val newReadStatus = userPreferencesHelper.toggleNoteReadStatus(noteId)
    //     _uiState.update { it.copy(isRead = newReadStatus) }
    // }
}
