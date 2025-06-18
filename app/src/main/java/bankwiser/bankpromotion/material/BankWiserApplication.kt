package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.AssetPackUpdateManager // New
import bankwiser.bankpromotion.material.data.local.DatabaseHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BankWiserApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val databaseHelper: DatabaseHelper by lazy { DatabaseHelper(this) }
    val userPreferencesHelper: UserPreferencesHelper by lazy { UserPreferencesHelper(this) }
    val contentRepository: ContentRepository by lazy { ContentRepository(databaseHelper) }
    val assetPackUpdateManager: AssetPackUpdateManager by lazy { // New
        AssetPackUpdateManager(this, databaseHelper, userPreferencesHelper, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // No longer doing contentRepository.checkAndPrepopulate() here,
        // as DatabaseHelper's init now handles initial DB copy if needed.

        // Start checking for updates periodically (e.g., after a delay on app start)
        applicationScope.launch {
            delay(10000) // Wait 10 seconds after app start
            assetPackUpdateManager.initializeAndCheckRemoteConfig()
        }
    }
}
