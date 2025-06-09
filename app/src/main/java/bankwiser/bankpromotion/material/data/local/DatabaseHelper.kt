package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "content.db"
        private const val DATABASE_VERSION = 1
        private const val ASSET_DB_PATH = "database/content_v1.db"
    }

    init {
        // This ensures the database is created (copied) when the helper is first instantiated.
        copyDatabaseFromAssets()
    }
    
    @Throws(IOException::class)
    private fun copyDatabaseFromAssets() {
        val dbPath: File = context.getDatabasePath(DATABASE_NAME)
        
        // If the database already exists, do nothing.
        if (dbPath.exists()) {
            return
        }

        // Make sure the directory exists.
        dbPath.parentFile?.mkdirs()

        try {
            val inputStream = context.assets.open(ASSET_DB_PATH)
            val outputStream = FileOutputStream(dbPath)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            // If we have an error, attempt to delete the file to avoid a half-copied state.
            if(dbPath.exists()) {
                dbPath.delete()
            }
            throw IOException("Error copying database from assets.", e)
        }
    }

    // These methods are required by SQLiteOpenHelper but we don't need them,
    // as we are providing a complete, pre-packaged database file.
    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}
