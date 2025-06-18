package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.Cursor
import android.util.Log
import bankwiser.bankpromotion.material.data.model.*
import net.sqlcipher.database.SQLiteDatabase // Changed to SQLCipher
import net.sqlcipher.database.SQLiteException // Changed to SQLCipher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// IMPORTANT: This key MUST match the key you use to encrypt your DB files.
// For production, this needs to be secured properly (NDK, server, etc.)
const val DATABASE_ENCRYPTION_KEY = "bankwiser" // Using the key from your last version

class DatabaseHelper(private val context: Context) {

    private val internalDbName = "content.db" // This will be the SQLCipher encrypted DB internally
    // This is the name of the encrypted DB file bundled in your app's assets folder
    private val initialBundledAssetDbName = "content_v1.db" // You confirmed you'll use simple names
    private val initialBundledAssetPath = "database/$initialBundledAssetDbName"

    companion object {
        private const val TAG = "DatabaseHelper"
        @Volatile // Ensures visibility across threads
        var isSqlCipherLoaded = false // Flag to load SQLCipher libs only once
    }

    init {
        // Ensure SQLCipher native libraries are loaded once per application lifecycle.
        synchronized(DatabaseHelper::class.java) {
            if (!isSqlCipherLoaded) {
                try {
                    SQLiteDatabase.loadLibs(context.applicationContext)
                    isSqlCipherLoaded = true
                    Log.i(TAG, "SQLCipher libraries loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "CRITICAL: Failed to load SQLCipher native libraries. Check SQLCipher dependency and ProGuard rules if applicable.", e)
                    // This is a fatal error for DB operations.
                    // Consider re-throwing or having a global error state.
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: An unexpected error occurred loading SQLCipher libraries.", e)
                }
            }
        }
    }

    private fun getInternalDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    private fun openDatabase(): SQLiteDatabase {
        val dbFile = getInternalDatabaseFile()

        // Ensure libraries are loaded before any database operation
        if (!isSqlCipherLoaded) {
            Log.w(TAG, "SQLCipher not loaded in init, attempting again in openDatabase.")
            synchronized(DatabaseHelper::class.java) {
                if (!isSqlCipherLoaded) {
                    SQLiteDatabase.loadLibs(context.applicationContext)
                    isSqlCipherLoaded = true
                }
            }
        }

        if (!dbFile.exists()) {
            Log.d(TAG, "Internal database '${dbFile.name}' does not exist. Copying initial bundled encrypted DB.")
            try {
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "Error copying initial bundled database to ${dbFile.absolutePath}", e)
                throw RuntimeException("Error creating source database from bundled asset", e)
            }
        }
        Log.d(TAG, "Opening internal encrypted database: ${dbFile.path}")
        try {
            // Open (or create if copy failed and file still doesn't exist) the database with the key
            return SQLiteDatabase.openOrCreateDatabase(dbFile, DATABASE_ENCRYPTION_KEY, null)
        } catch (e: SQLiteException) { // SQLCipher's SQLiteException
            Log.e(TAG, "SQLCipher Error opening/creating database: ${e.message}. Key used: '$DATABASE_ENCRYPTION_KEY'. File: ${dbFile.absolutePath}", e)
            // This error often means wrong key, corrupted DB, or SQLCipher not properly initialized.
            // Consider deleting the potentially corrupt DB and trying to recopy the bundled one as a recovery attempt.
            if (dbFile.exists()) {
                Log.w(TAG, "Attempting to delete potentially corrupted DB and recopy: ${dbFile.name}")
                dbFile.delete()
            }
            try {
                copyInitialBundledDatabase(dbFile)
                return SQLiteDatabase.openOrCreateDatabase(dbFile, DATABASE_ENCRYPTION_KEY, null) // Retry open
            } catch (ioe: IOException) {
                 Log.e(TAG, "Failed to recopy bundled DB after open error.", ioe)
                 throw RuntimeException("Failed to recover database after open error.", ioe)
            } catch (sqle: SQLiteException) {
                Log.e(TAG, "Still failed to open DB after recopy attempt.", sqle)
                throw RuntimeException("SQLCipher still failed to open DB after recopy.", sqle)
            }
        }
    }

    private fun copyInitialBundledDatabase(destinationDbFile: File) {
        context.assets.open(initialBundledAssetPath).use { inputStream ->
            destinationDbFile.parentFile?.mkdirs() // Ensure directory exists
            FileOutputStream(destinationDbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                Log.i(TAG, "Initial bundled encrypted database '$initialBundledAssetDbName' copied to '${destinationDbFile.name}'")
            }
        }
    }

    /**
     * Replaces the current internal database with a new encrypted database file.
     * This is typically used after downloading an update from Play Asset Delivery.
     * @param newEncryptedDbFile The File object pointing to the new, encrypted database.
     * @return True if replacement was successful, false otherwise.
     */
    fun replaceDatabase(newEncryptedDbFile: File): Boolean {
        val internalDbFile = getInternalDatabaseFile()
        Log.i(TAG, "Attempting to replace internal DB '${internalDbFile.name}' with new DB from '${newEncryptedDbFile.name}'")

        // It's crucial that no connections are open to the internalDbFile when deleting/replacing.
        // This DatabaseHelper creates a new connection for each readData call, so it should be okay
        // from this class's perspective. However, other parts of the app must not hold a connection.
        // For robustness, you might need a global mechanism to signal DB closure before replacement.

        try {
            if (internalDbFile.exists()) {
                if (!internalDbFile.delete()) {
                    Log.e(TAG, "Failed to delete old internal database: ${internalDbFile.absolutePath}")
                    return false
                }
                Log.d(TAG, "Old internal database deleted: ${internalDbFile.name}")
            } else {
                Log.w(TAG, "Old internal database did not exist at: ${internalDbFile.absolutePath}")
            }

            newEncryptedDbFile.copyTo(internalDbFile, overwrite = true)
            Log.i(TAG, "New encrypted database from '${newEncryptedDbFile.name}' successfully copied to '${internalDbFile.name}'")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing database with new version from '${newEncryptedDbFile.name}'", e)
            // Attempt to restore the original bundled DB if replacement fails catastrophically?
            // Or leave it in a state where next openDatabase will try to copy initial again if internalDbFile is missing.
            return false
        }
    }

    private inline fun <T> readData(queryBlock: (SQLiteDatabase) -> T): T {
        var db: SQLiteDatabase? = null // Ensure db is nullable for the finally block
        try {
            db = openDatabase()
            return queryBlock(db)
        } catch (e: SQLiteException) { // Catches net.sqlcipher.database.SQLiteException
            Log.e(TAG, "SQLCipher Query Exception: ${e.message}", e)
            throw e
        } catch (e: Exception) { // Catch other potential exceptions
            Log.e(TAG, "General DB Read Exception: ${e.message}", e)
            throw e
        } finally {
            try {
                db?.close()
            } catch (e: SQLiteException) { // Catches net.sqlcipher.database.SQLiteException
                Log.e(TAG, "SQLCipher Error closing DB in finally block: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "General Error closing DB in finally block: ${e.message}", e)
            }
        }
    }
    
    // Helper function to get column index safely, preventing crashes if column name is misspelled or missing
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
        // SQLite stores booleans as INTEGER 0 (false) or 1 (true).
        return if (index != -1 && !this.isNull(index)) this.getInt(index) == 1 else false
    }

    // --- Data Access Methods ---
    // The actual SQL queries remain the same as your Phase 6 working version.
    // They will now operate on the SQLCipher-encrypted database.

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
