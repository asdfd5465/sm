package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class SavedStateViewModelFactory(private val repository: ContentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        if (modelClass.isAssignableFrom(SubCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SubCategoryViewModel(repository, savedStateHandle) as T
        }
        if (modelClass.isAssignableFrom(NoteListViewModel::class.java)) { // This should be TopicContentViewModel
            @Suppress("UNCHECKED_CAST")
            return TopicContentViewModel(repository, savedStateHandle) as T // Changed
        }
        if (modelClass.isAssignableFrom(NoteDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteDetailViewModel(repository, savedStateHandle) as T
        }
        if (modelClass.isAssignableFrom(TopicContentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopicContentViewModel(repository, savedStateHandle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
