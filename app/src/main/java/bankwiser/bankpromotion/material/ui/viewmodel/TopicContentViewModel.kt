package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.model.AudioContent
import bankwiser.bankpromotion.material.data.model.Faq
import bankwiser.bankpromotion.material.data.model.Mcq
import bankwiser.bankpromotion.material.data.model.Note
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TopicContentUiState(
    val notes: List<Note> = emptyList(),
    val faqs: List<Faq> = emptyList(),
    val mcqs: List<Mcq> = emptyList(),
    val audioContent: List<AudioContent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val subCategoryName: String = "" // Will fetch this later if needed
)

class TopicContentViewModel(
    private val repository: ContentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val subCategoryId: String = savedStateHandle.get<String>("subCategoryId")!!

    private val _uiState = MutableStateFlow(TopicContentUiState(isLoading = true))
    val uiState: StateFlow<TopicContentUiState> = _uiState

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val notes = withContext(Dispatchers.IO) { repository.getNotes(subCategoryId) }
                val faqs = withContext(Dispatchers.IO) { repository.getFaqs(subCategoryId) }
                val mcqs = withContext(Dispatchers.IO) { repository.getMcqs(subCategoryId) }
                val audio = withContext(Dispatchers.IO) { repository.getAudioContent(subCategoryId) }
                _uiState.value = TopicContentUiState(
                    notes = notes,
                    faqs = faqs,
                    mcqs = mcqs,
                    audioContent = audio,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
