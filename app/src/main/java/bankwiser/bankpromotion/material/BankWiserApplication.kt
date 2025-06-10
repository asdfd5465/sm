package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.ContentDatabase
import bankwiser.bankpromotion.material.data.repository.ContentRepository

class BankWiserApplication : Application() {
    // Expose a lazy-initialized instance of the database
    val contentDatabase: ContentDatabase by lazy {
        ContentDatabase.getDatabase(this)
    }

    // Expose a lazy-initialized instance of the repository
    val contentRepository: ContentRepository by lazy {
        ContentRepository(contentDatabase)
    }
}
