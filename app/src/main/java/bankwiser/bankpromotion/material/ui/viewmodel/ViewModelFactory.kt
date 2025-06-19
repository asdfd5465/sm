package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application // Required for AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class ViewModelFactory(
    private val repository: ContentRepository,
    private val application: Application // Add application context
    ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                HomeViewModel(repository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SearchViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                ProfileViewModel(application) as T // ProfileViewModel needs Application
            }
            // SubscriptionViewModel is an AndroidViewModel, usually created with AndroidViewModelFactory
            // or a custom factory if it has other constructor deps.
            // For now, let's assume it's created with its default factory if no other deps.
            // If SubscriptionViewModel needed ContentRepository, it would be added here.
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name} in ViewModelFactory")
        }
    }
}
