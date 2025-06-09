package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class NoteListViewModel(private val repository: ContentRepository, private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val subCategoryId: StateFlow<String> = savedStateHandle.getStateFlow("subCategoryId", "")

    val notes: StateFlow<List<NoteEntity>> = subCategoryId.flatMapLatest { id ->
        repository.getNotes(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
