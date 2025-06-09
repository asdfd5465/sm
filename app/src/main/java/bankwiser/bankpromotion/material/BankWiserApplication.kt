package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.ContentDatabase
import bankwiser.bankpromotion.material.data.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankWiserApplication : Application() {

    // Using by lazy ensures the database and repository are only created when they're first needed
    val contentDatabase: ContentDatabase by lazy { ContentDatabase.getDatabase(this) }
    val contentRepository: ContentRepository by lazy { ContentRepository(contentDatabase, this) }

    override fun onCreate() {
        super.onCreate()
        // We launch a coroutine to do the one-time database population check
        // This won't block the main thread
        CoroutineScope(Dispatchers.IO).launch {
            contentRepository.checkAndPrepopulate()
        }
    }
}
