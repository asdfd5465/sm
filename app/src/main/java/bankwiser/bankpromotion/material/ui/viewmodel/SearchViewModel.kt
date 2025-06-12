package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.model.AudioContent
import bankwiser.bankpromotion.material.data.model.Faq
import bankwiser.bankpromotion.material.data.model.Mcq
import bankwiser.bankpromotion.material.data.model.Note
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResultUiState(
    val notes: List<Note> = emptyList(),
    val faqs: List<Faq> = emptyList(),
    val mcqs: List<Mcq> = emptyList(),
    val audioContent: List<AudioContent> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
    val noResults: Boolean = false,
    val initialScreen: Boolean = true // To track if it's the initial empty state
)

class SearchViewModel(private val repository: ContentRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchResultUiState())
    val uiState: StateFlow<SearchResultUiState> = _uiState

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel() // Cancel previous search if any

        if (query.isBlank()) {
            _uiState.value = SearchResultUiState(query = query, initialScreen = true) // Reset to initial empty state
            return
        }
        _uiState.value = _uiState.value.copy(query = query, isLoading = true, noResults = false, initialScreen = false)

        if (query.length < 2) { // Perform search only if query is long enough
            _uiState.value = _uiState.value.copy(
                notes = emptyList(),
                faqs = emptyList(),
                mcqs = emptyList(),
                audioContent = emptyList(),
                isLoading = false,
                initialScreen = false // Not initial screen anymore
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce: wait for 300ms after user stops typing
            try {
                val notesResult = withContext(Dispatchers.IO) { repository.searchNotesByTitle(query) }
                val faqsResult = withContext(Dispatchers.IO) { repository.searchFaqsByQuestion(query) }
                val mcqsResult = withContext(Dispatchers.IO) { repository.searchMcqsByQuestionText(query) }
                val audioResult = withContext(Dispatchers.IO) { repository.searchAudioByTitle(query) }

                val noResultsFound = notesResult.isEmpty() && faqsResult.isEmpty() && mcqsResult.isEmpty() && audioResult.isEmpty()
                _uiState.value = _uiState.value.copy(
                    notes = notesResult,
                    faqs = faqsResult,
                    mcqs = mcqsResult,
                    audioContent = audioResult,
                    isLoading = false,
                    noResults = noResultsFound,
                    initialScreen = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, initialScreen = false) // Handle error
            }
        }
    }
}
