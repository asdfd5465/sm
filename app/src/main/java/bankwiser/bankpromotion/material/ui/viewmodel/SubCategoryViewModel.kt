package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SubCategoryViewModel(private val repository: ContentRepository, private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val categoryId: StateFlow<String> = savedStateHandle.getStateFlow("categoryId", "")

    val subCategories: StateFlow<List<SubCategoryEntity>> = categoryId.flatMapLatest { id ->
        repository.getSubCategories(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
