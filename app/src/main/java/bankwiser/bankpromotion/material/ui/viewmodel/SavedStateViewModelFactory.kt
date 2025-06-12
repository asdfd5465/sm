package bankwiser.bankpromotion.material.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import bankwiser.bankpromotion.material.BankWiserApplication // Import Application
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class SavedStateViewModelFactory(
    private val repository: ContentRepository,
    private val application: BankWiserApplication // Pass Application to get UserPreferencesHelper
    ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        val userPrefsHelper = application.userPreferencesHelper // Get instance

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
                // Pass userPrefsHelper to NoteDetailViewModel
                NoteDetailViewModel(repository, userPrefsHelper, savedStateHandle) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
