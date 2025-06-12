package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import bankwiser.bankpromotion.material.BankWiserApplication // Ensure this is imported
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class SavedStateViewModelFactory(
    private val repository: ContentRepository,
    private val application: BankWiserApplication // Added application parameter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        // Get UserPreferencesHelper via the application instance
        val userPrefsHelper = application.userPreferencesHelper

        return when {
            modelClass.isAssignableFrom(SubCategoryViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                // SubCategoryViewModel currently doesn't need userPrefsHelper, but the factory structure requires it
                SubCategoryViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(TopicContentViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                // TopicContentViewModel currently doesn't need userPrefsHelper directly
                TopicContentViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(NoteDetailViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                NoteDetailViewModel(repository, userPrefsHelper, savedStateHandle) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
