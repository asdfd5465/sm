package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.Cursor
import android.util.Log
import bankwiser.bankpromotion.material.data.model.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
import net.sqlcipher.database.SQLiteException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val DATABASE_ENCRYPTION_KEY = "bankwiser" // Your key

class DatabaseHelper(private val context: Context) {

    private val internalDbName = "content.db"
    private val initialBundledAssetDbName = "content_v1_initial_bundled.db" // As per your rename
    private val initialBundledAssetPath = "database/$initialBundledAssetDbName"

    companion object {
        private const val TAG = "DatabaseHelper"
        @Volatile
        private var isSqlCipherLoaded = false // Private to ensure controlled by ensureSqlCipherLoaded
        private val loadLock = Any() // Lock for loading libraries
    }

    init {
        ensureSqlCipherLoaded(context.applicationContext)
    }

    // Public static method to ensure libraries are loaded, can be called from Application.onCreate
    // to attempt loading as early as possible.
    fun ensureSqlCipherLoaded(appContext: Context) {
        synchronized(loadLock) {
            if (!isSqlCipherLoaded) {
                try {
                    Log.i(TAG, "Attempting to load SQLCipher libraries...")
                    SQLiteDatabase.loadLibs(appContext)
                    isSqlCipherLoaded = true
                    Log.i(TAG, "SQLCipher libraries loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "CRITICAL: UnsatisfiedLinkError loading SQLCipher. Native library issue.", e)
                    // This is often due to missing .so files for the device's ABI or incorrect packaging.
                    // Consider re-throwing or a global error flag.
                    throw RuntimeException("Failed to load SQLCipher native libraries (UnsatisfiedLinkError)", e)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: An unexpected error occurred loading SQLCipher libraries.", e)
                    throw RuntimeException("Unexpected error loading SQLCipher libraries", e)
                }
            } else {
                Log.d(TAG, "SQLCipher libraries already loaded.")
            }
        }
    }

    private fun getInternalDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    // This hook will be executed immediately after the database connection is opened,
    // but before any other operations (including keying) are performed.
    private val sqlCipherHook = object : SQLiteDatabaseHook {
        override fun preKey(database: SQLiteDatabase?) {
            // Not typically used for v3 compatibility setting, but available.
        }
        override fun postKey(database: SQLiteDatabase?) {
            // This is where we set PRAGMAs for v3 compatibility AFTER the key is applied
            // However, for opening a v3 DB with v4 library, compatibility PRAGMA should be BEFORE key.
            // The modern approach is to set PRAGMAs on the connection after open but before key.
            // For this specific "file is not a database" when keying, the key must be set AFTER compatibility.
            // So we will set PRAGMAs on the db instance after opening with empty key, then key.
        }
    }


    private fun openDatabase(isRetryAttempt: Boolean = false): SQLiteDatabase {
        ensureSqlCipherLoaded(context.applicationContext) // Ensure libs are loaded
        val dbFile = getInternalDatabaseFile()

        if (!dbFile.exists()) {
            Log.d(TAG, "Internal DB '${dbFile.name}' not found. Copying initial bundled version.")
            try {
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "FATAL: Error copying initial bundled DB.", e)
                throw RuntimeException("Error creating source DB from asset for path: ${dbFile.absolutePath}", e)
            }
        }

        Log.d(TAG, "Attempting to open DB: ${dbFile.path} with key: '$DATABASE_ENCRYPTION_KEY'")
        var db: SQLiteDatabase? = null
        try {
            // SQLCipher 4.x opening a 3.x encrypted database:
            // 1. Open with an empty key.
            // 2. Set PRAGMA cipher_compatibility = 3.
            // 3. Set PRAGMA key = 'your_real_key'.
            // 4. Perform a test query.

            // Try to open the database file using an empty passphrase first to allow setting PRAGMAs
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, sqlCipherHook)
            Log.i(TAG, "DB opened with empty key to set PRAGMAs.")

            // Set PRAGMAs for SQLCipher 3.x compatibility
            // These are common settings for v3 databases.
            db.execSQL("PRAGMA cipher_compatibility = 3;")
            // db.execSQL("PRAGMA kdf_iter = 64000;") // Default for SQLCipher v3
            // db.execSQL("PRAGMA cipher_page_size = 1024;") // Default for SQLCipher v3
            // db.execSQL("PRAGMA cipher_hmac_algorithm = HMAC_SHA1;") // Default for SQLCipher v3
            // db.execSQL("PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;") // Default for SQLCipher v3
            Log.i(TAG, "Executed: PRAGMA cipher_compatibility = 3;")

            // Now, apply the actual encryption key
            db.rawQuery("PRAGMA key = '$DATABASE_ENCRYPTION_KEY';").close() // Use rawQuery and close cursor for PRAGMA key
            Log.i(TAG, "PRAGMA key = '$DATABASE_ENCRYPTION_KEY' executed.")

            // Verify decryption with a simple query
            db.rawQuery("SELECT count(*) FROM sqlite_master;", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    Log.i(TAG, "SQLCipher DB opened and keyed successfully. sqlite_master count: $count")
                } else {
                    Log.e(TAG, "Verification query (sqlite_master count) failed after keying.")
                    throw SQLiteException("Verification query failed after keying (sqlite_master count gave no rows).")
                }
            }
            return db
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLCipher Error during open/keying process: ${e.message}. Path: ${dbFile.absolutePath}", e)
            db?.close() // Ensure it's closed if something went wrong

            if (!isRetryAttempt && dbFile.exists()) {
                Log.w(TAG, "Open/keying failed. Deleting potentially corrupt/mismatched DB and retrying with fresh asset copy ONCE.")
                dbFile.delete()
                // The copyInitialBundledDatabase will be called by the next openDatabase call if file doesn't exist.
                // Recursive call with a flag to prevent infinite loop if the bundled asset itself is problematic.
                return openDatabase(isRetryAttempt = true)
            } else if (isRetryAttempt) {
                Log.e(TAG, "FATAL: SQLCipher still failed to open/key DB after deleting and recopying from assets. Key: '$DATABASE_ENCRYPTION_KEY'. This likely means the bundled asset is not correctly encrypted for this key/settings, or SQLCipher native libs are not working.", e)
                throw RuntimeException("SQLCipher open/key failed after retry with fresh asset. Check bundled DB encryption and native libs.", e)
            } else { // File didn't exist and copy failed, or some other state
                 Log.e(TAG, "FATAL: SQLCipher open/key failed and not a retry scenario. Key: '$DATABASE_ENCRYPTION_KEY'", e)
                throw RuntimeException("SQLCipher open/key failed. Check bundled DB and logs.", e)
            }
        } catch (e: Exception) { // Catch any other unexpected errors
            Log.e(TAG, "Unexpected general error during database open/keying process.", e)
            db?.close()
            throw RuntimeException("Unexpected error opening database", e)
        }
    }

    private fun copyInitialBundledDatabase(destinationDbFile: File) {
        Log.i(TAG, "Copying initial bundled asset '$initialBundledAssetPath' to '${destinationDbFile.absolutePath}'")
        try {
            context.assets.open(initialBundledAssetPath).use { inputStream ->
                destinationDbFile.parentFile?.mkdirs()
                FileOutputStream(destinationDbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    Log.i(TAG, "Successfully copied initial bundled encrypted database.")
                }
            }
        } catch (ioe: IOException) {
            Log.e(TAG, "IOException during copyInitialBundledDatabase for ${destinationDbFile.name}", ioe)
            throw ioe // Re-throw to be caught by caller
        }
    }

    fun replaceDatabase(newEncryptedDbFile: File): Boolean {
        val internalDbFile = getInternalDatabaseFile()
        Log.i(TAG, "Attempting to replace internal DB '${internalDbFile.name}' with new DB from '${newEncryptedDbFile.name}'")
        try {
            if (internalDbFile.exists()) {
                if (!internalDbFile.delete()) {
                    Log.e(TAG, "Failed to delete old internal database: ${internalDbFile.absolutePath}")
                    return false
                }
                Log.d(TAG, "Old internal database deleted: ${internalDbFile.name}")
            }
            newEncryptedDbFile.copyTo(internalDbFile, overwrite = true)
            Log.i(TAG, "New encrypted database from '${newEncryptedDbFile.name}' successfully copied to '${internalDbFile.name}'")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing database with new version from '${newEncryptedDbFile.name}'", e)
            return false
        }
    }

    private inline fun <T> readData(queryBlock: (SQLiteDatabase) -> T): T {
        var db: SQLiteDatabase? = null
        try {
            db = openDatabase()
            return queryBlock(db)
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLCipher Query Exception: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "General DB Read Exception: ${e.message}", e)
            throw e
        } finally {
            try {
                db?.close()
            } catch (e: SQLiteException) {
                Log.e(TAG, "SQLCipher Error closing DB in finally block: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "General Error closing DB in finally block: ${e.message}", e)
            }
        }
    }

    private fun Cursor.getColumnIndexSafe(columnName: String): Int {
        return try { this.getColumnIndexOrThrow(columnName) } catch (e: IllegalArgumentException) { -1 }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexSafe(columnName)
        return if (index != -1 && !this.isNull(index)) this.getString(index) else null
    }

    private fun Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndexSafe(columnName)
        return if (index != -1 && !this.isNull(index)) this.getInt(index) else null
    }

    private fun Cursor.getBoolean(columnName: String): Boolean {
        val index = getColumnIndexSafe(columnName)
        return if (index != -1 && !this.isNull(index)) this.getInt(index) == 1 else false
    }

    // --- Data Access Methods (These remain unchanged from your version) ---
    fun getAllCategories(): List<Category> = readData { db ->
        val categories = mutableListOf<Category>()
        db.rawQuery("SELECT category_id, category_name FROM categories", null).use { cursor ->
            while (cursor.moveToNext()) {
                categories.add(
                    Category(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
                    )
                )
            }
        }
        categories
    }

    fun getSubCategories(categoryId: String): List<SubCategory> = readData { db ->
        val subCategories = mutableListOf<SubCategory>()
        db.rawQuery("SELECT sub_category_id, category_id, sub_category_name FROM subcategories WHERE category_id = ?", arrayOf(categoryId)).use { cursor ->
            while (cursor.moveToNext()) {
                subCategories.add(
                    SubCategory(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("sub_category_id")),
                        categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("sub_category_name"))
                    )
                )
            }
        }
        subCategories
    }

    fun getNotes(subCategoryId: String): List<Note> = readData { db ->
        val notes = mutableListOf<Note>()
        val query = "SELECT note_id, title, body, sub_category_id, is_free_launch_content, is_premium FROM notes WHERE sub_category_id = ? AND is_deleted = 0"
        db.rawQuery(query, arrayOf(subCategoryId)).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(
                    Note(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("note_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        notes
    }

    fun getNote(noteId: String): Note? = readData { db ->
        val query = "SELECT note_id, title, body, sub_category_id, is_free_launch_content, is_premium FROM notes WHERE note_id = ? AND is_deleted = 0"
        db.rawQuery(query, arrayOf(noteId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return@readData Note(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("note_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    subCategoryId = cursor.getStringOrNull("sub_category_id"),
                    isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                    isPremium = cursor.getBoolean("is_premium")
                )
            }
        }
        null
    }

    fun getFaqs(subCategoryId: String): List<Faq> = readData { db ->
        val faqs = mutableListOf<Faq>()
        val query = "SELECT faq_id, question, answer, sub_category_id, is_free_launch_content, is_premium FROM faqs WHERE sub_category_id = ? AND is_deleted = 0"
        db.rawQuery(query, arrayOf(subCategoryId)).use { cursor ->
            while (cursor.moveToNext()) {
                faqs.add(
                    Faq(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("faq_id")),
                        question = cursor.getString(cursor.getColumnIndexOrThrow("question")),
                        answer = cursor.getString(cursor.getColumnIndexOrThrow("answer")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        faqs
    }

    fun getMcqs(subCategoryId: String): List<Mcq> = readData { db ->
        val mcqs = mutableListOf<Mcq>()
        val query = "SELECT mcq_id, question_text, option_a, option_b, option_c, option_d, correct_option, sub_category_id, is_free_launch_content, is_premium FROM mcqs WHERE sub_category_id = ? AND is_deleted = 0"
        db.rawQuery(query, arrayOf(subCategoryId)).use { cursor ->
            while (cursor.moveToNext()) {
                mcqs.add(
                    Mcq(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("mcq_id")),
                        questionText = cursor.getString(cursor.getColumnIndexOrThrow("question_text")),
                        optionA = cursor.getString(cursor.getColumnIndexOrThrow("option_a")),
                        optionB = cursor.getString(cursor.getColumnIndexOrThrow("option_b")),
                        optionC = cursor.getString(cursor.getColumnIndexOrThrow("option_c")),
                        optionD = cursor.getString(cursor.getColumnIndexOrThrow("option_d")),
                        correctOption = cursor.getString(cursor.getColumnIndexOrThrow("correct_option")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        mcqs
    }

    fun getAudioContent(subCategoryId: String): List<AudioContent> = readData { db ->
        val audioList = mutableListOf<AudioContent>()
        val query = "SELECT audio_id, title, audio_url, duration_seconds, sub_category_id, is_free_launch_content, is_premium FROM audiocontent WHERE sub_category_id = ? AND is_deleted = 0"
        db.rawQuery(query, arrayOf(subCategoryId)).use { cursor ->
            while (cursor.moveToNext()) {
                audioList.add(
                    AudioContent(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("audio_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        audioUrl = cursor.getString(cursor.getColumnIndexOrThrow("audio_url")),
                        durationSeconds = cursor.getIntOrNull("duration_seconds"),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        audioList
    }

    fun searchNotesByTitle(query: String): List<Note> = readData { db ->
        val notes = mutableListOf<Note>()
        val sql = "SELECT note_id, title, body, sub_category_id, is_free_launch_content, is_premium FROM notes WHERE title LIKE ? AND is_deleted = 0"
        db.rawQuery(sql, arrayOf("%$query%")).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(
                     Note(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("note_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        notes
    }

    fun searchFaqsByQuestion(query: String): List<Faq> = readData { db ->
        val faqs = mutableListOf<Faq>()
        val sql = "SELECT faq_id, question, answer, sub_category_id, is_free_launch_content, is_premium FROM faqs WHERE question LIKE ? AND is_deleted = 0"
        db.rawQuery(sql, arrayOf("%$query%")).use { cursor ->
            while (cursor.moveToNext()) {
                faqs.add(
                    Faq(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("faq_id")),
                        question = cursor.getString(cursor.getColumnIndexOrThrow("question")),
                        answer = cursor.getString(cursor.getColumnIndexOrThrow("answer")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        faqs
    }

    fun searchMcqsByQuestionText(query: String): List<Mcq> = readData { db ->
        val mcqs = mutableListOf<Mcq>()
        val sql = "SELECT mcq_id, question_text, option_a, option_b, option_c, option_d, correct_option, sub_category_id, is_free_launch_content, is_premium FROM mcqs WHERE question_text LIKE ? AND is_deleted = 0"
        db.rawQuery(sql, arrayOf("%$query%")).use { cursor ->
            while (cursor.moveToNext()) {
                mcqs.add(
                    Mcq(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("mcq_id")),
                        questionText = cursor.getString(cursor.getColumnIndexOrThrow("question_text")),
                        optionA = cursor.getString(cursor.getColumnIndexOrThrow("option_a")),
                        optionB = cursor.getString(cursor.getColumnIndexOrThrow("option_b")),
                        optionC = cursor.getString(cursor.getColumnIndexOrThrow("option_c")),
                        optionD = cursor.getString(cursor.getColumnIndexOrThrow("option_d")),
                        correctOption = cursor.getString(cursor.getColumnIndexOrThrow("correct_option")),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        mcqs
    }

    fun searchAudioByTitle(query: String): List<AudioContent> = readData { db ->
        val audioList = mutableListOf<AudioContent>()
        val sql = "SELECT audio_id, title, audio_url, duration_seconds, sub_category_id, is_free_launch_content, is_premium FROM audiocontent WHERE title LIKE ? AND is_deleted = 0"
        db.rawQuery(sql, arrayOf("%$query%")).use { cursor ->
            while (cursor.moveToNext()) {
                audioList.add(
                    AudioContent(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("audio_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        audioUrl = cursor.getString(cursor.getColumnIndexOrThrow("audio_url")),
                        durationSeconds = cursor.getIntOrNull("duration_seconds"),
                        subCategoryId = cursor.getStringOrNull("sub_category_id"),
                        isFreeLaunchContent = cursor.getBoolean("is_free_launch_content"),
                        isPremium = cursor.getBoolean("is_premium")
                    )
                )
            }
        }
        audioList
    }
}
