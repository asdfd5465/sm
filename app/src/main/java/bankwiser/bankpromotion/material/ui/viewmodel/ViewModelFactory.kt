package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            // The factory now creates the repository itself, ensuring a single source.
            val repository = ContentRepository((application as BankWiserApplication).contentDatabase)
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class. Use SavedStateViewModelFactory for ViewModels with arguments.")
    }
}
