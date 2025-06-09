package bankwiser.bankpromotion.material

import android.app.Application
import bankwiser.bankpromotion.material.data.local.ContentDatabase

class BankWiserApplication : Application() {
    val contentDatabase: ContentDatabase by lazy {
        ContentDatabase.getDatabase(this)
    }
}
