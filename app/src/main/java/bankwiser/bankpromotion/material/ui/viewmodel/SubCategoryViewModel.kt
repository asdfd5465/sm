package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.data.model.SubCategory
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubCategoryViewModel(private val repository: ContentRepository, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val categoryId: String = savedStateHandle.get<String>("categoryId")!!
    
    private val _subCategories = MutableStateFlow<List<SubCategory>>(emptyList())
    val subCategories: StateFlow<List<SubCategory>> = _subCategories

    init {
        loadSubCategories()
    }
    
    private fun loadSubCategories() {
        viewModelScope.launch {
            _subCategories.value = withContext(Dispatchers.IO) {
                repository.getSubCategories(categoryId)
            }
        }
    }
}
