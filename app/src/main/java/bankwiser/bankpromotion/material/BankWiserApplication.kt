package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.crypto.FileEncryptionHelper
import bankwiser.bankpromotion.material.crypto.KeyStoreHelper
import bankwiser.bankpromotion.material.data.AssetPackUpdateManager
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
    val assetPackUpdateManager: AssetPackUpdateManager by lazy {
        AssetPackUpdateManager(this, databaseHelper, userPreferencesHelper, applicationScope)
    }
    // Crypto Helpers
    val keyStoreHelper: KeyStoreHelper by lazy { KeyStoreHelper() }
    val fileEncryptionHelper: FileEncryptionHelper by lazy { FileEncryptionHelper(keyStoreHelper) }


    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            delay(10000) 
            assetPackUpdateManager.initializeAndCheckRemoteConfig()
        }
    }
}
