package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.model.Faq
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
    // Add MCQs and Audio later
    val isLoading: Boolean = false,
    val query: String = "",
    val noResults: Boolean = false
)

class SearchViewModel(private val repository: ContentRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchResultUiState())
    val uiState: StateFlow<SearchResultUiState> = _uiState

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, isLoading = true, noResults = false)
        searchJob?.cancel() // Cancel previous search if any
        if (query.length < 2) { // Perform search only if query is long enough
            _uiState.value = _uiState.value.copy(notes = emptyList(), faqs = emptyList(), isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce: wait for 300ms after user stops typing
            try {
                val notesResult = withContext(Dispatchers.IO) { repository.searchNotesByTitle(query) }
                val faqsResult = withContext(Dispatchers.IO) { repository.searchFaqsByQuestion(query) }
                val noResultsFound = notesResult.isEmpty() && faqsResult.isEmpty()
                _uiState.value = _uiState.value.copy(
                    notes = notesResult,
                    faqs = faqsResult,
                    isLoading = false,
                    noResults = noResultsFound
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false) // Handle error
            }
        }
    }
}
