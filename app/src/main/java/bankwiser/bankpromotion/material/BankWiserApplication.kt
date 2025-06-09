package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.ContentDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankWiserApplication : Application() {
    // A single, application-wide scope for background tasks.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Use 'lazy' so the database and its population is only done once when first needed.
    val contentDatabase: ContentDatabase by lazy {
        val db = ContentDatabase.getDatabase(this)
        // Launch a one-time check and population task.
        applicationScope.launch {
            // Check if the database is empty by counting categories.
            if (db.categoryDao().count() == 0) {
                // If it's empty, populate it from the asset.
                ContentDatabase.prePopulateFromAsset(this@BankWiserApplication, db)
            }
        }
        db
    }
}
