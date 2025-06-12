package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class SavedStateViewModelFactory(private val repository: ContentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        
        return when {
            modelClass.isAssignableFrom(SubCategoryViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SubCategoryViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(TopicContentViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                TopicContentViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(NoteDetailViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                NoteDetailViewModel(repository, savedStateHandle) as T
            }
            // NoteListViewModel is no longer actively used as TopicContentViewModel handles its content.
            // If it's still somehow being instantiated, it would cause issues.
            // For now, ensuring TopicContentViewModel is correctly handled is key.
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
