package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class SavedStateViewModelFactory(
    private val repository: ContentRepository,
    private val application: BankWiserApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        val userPrefsHelper = application.userPreferencesHelper

        return when {
            modelClass.isAssignableFrom(SubCategoryViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SubCategoryViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(TopicContentViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                // TopicContentViewModel doesn't directly take userPrefsHelper,
                // but it might if its sub-composables need it for items.
                // For now, it only needs repository and savedStateHandle.
                TopicContentViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(NoteDetailViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                NoteDetailViewModel(repository, userPrefsHelper, savedStateHandle) as T
            }
            // NoteListViewModel is removed as TopicContentScreen handles its functionality
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
