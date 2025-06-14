package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.DatabaseHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper // New Import
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class BankWiserApplication : Application() {
    private val databaseHelper: DatabaseHelper by lazy { DatabaseHelper(this) }
    val contentRepository: ContentRepository by lazy { ContentRepository(databaseHelper) }
    val userPreferencesHelper: UserPreferencesHelper by lazy { UserPreferencesHelper(this) } // New
}
