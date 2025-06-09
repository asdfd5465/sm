package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.ContentDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class BankWiserApplication : Application() {
    // Create an application-wide scope for coroutines.
    val applicationScope = CoroutineScope(SupervisorJob())

    // Use 'lazy' to initialize the database only when first accessed.
    val contentDatabase: ContentDatabase by lazy {
        ContentDatabase.getDatabase(this, applicationScope)
    }
}
