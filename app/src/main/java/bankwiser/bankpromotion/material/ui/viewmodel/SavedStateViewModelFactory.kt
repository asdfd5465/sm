package bankwiser.bankpromotion.material.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import bankwiser.bankpromotion.material.data.repository.ContentRepository

/**
 * A factory that can create ViewModels that require a ContentRepository and a SavedStateHandle.
 * This is the standard way to pass arguments from navigation into a ViewModel.
 */
class SavedStateViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val repository: ContentRepository, // This was missing/incorrect
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        // Here, we pass the repository from the factory's property to the ViewModel's constructor
        if (modelClass.isAssignableFrom(SubCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SubCategoryViewModel(repository, handle) as T
        }
        if (modelClass.isAssignableFrom(NoteListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteListViewModel(repository, handle) as T
        }
        if (modelClass.isAssignableFrom(NoteDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteDetailViewModel(repository, handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
