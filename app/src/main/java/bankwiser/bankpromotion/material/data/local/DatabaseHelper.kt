package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.Cursor
import android.util.Log
import bankwiser.bankpromotion.material.data.model.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteException // Correct import
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val DATABASE_ENCRYPTION_KEY = "bankwiser" // Your key from the repo

class DatabaseHelper(private val context: Context) {

    private val internalDbName = "content.db"
    // This MUST match the renamed file in app/src/main/assets/database/
    private val initialBundledAssetDbName = "content_v1_initial_bundled.db" 
    private val initialBundledAssetPath = "database/$initialBundledAssetDbName"

    companion object {
        private const val TAG = "DatabaseHelper"
        @Volatile
        var isSqlCipherLoaded = false
    }

    init {
        ensureSqlCipherLoaded()
    }

    // Centralized method to ensure SQLCipher libraries are loaded.
    private fun ensureSqlCipherLoaded() {
        synchronized(DatabaseHelper::class.java) {
            if (!isSqlCipherLoaded) {
                try {
                    SQLiteDatabase.loadLibs(context.applicationContext)
                    isSqlCipherLoaded = true
                    Log.i(TAG, "SQLCipher libraries loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "CRITICAL: UnsatisfiedLinkError loading SQLCipher. Native library issue. Check ABI filters, packagingOptions, and ProGuard.", e)
                    // This error often means the .so files are missing or can't be linked.
                    throw RuntimeException("Failed to load SQLCipher native libraries", e) // Re-throw to make failure obvious
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: An unexpected error occurred loading SQLCipher libraries.", e)
                    throw RuntimeException("Unexpected error loading SQLCipher libraries", e) // Re-throw
                }
            }
        }
    }

    private fun getInternalDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    private fun openDatabase(): SQLiteDatabase {
        ensureSqlCipherLoaded() // Call before any DB operation

        val dbFile = getInternalDatabaseFile()

        if (!dbFile.exists()) {
            Log.d(TAG, "Internal database '${dbFile.name}' does not exist. Attempting to copy initial bundled encrypted DB.")
            try {
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "FATAL: Error copying initial bundled database to ${dbFile.absolutePath}", e)
                throw RuntimeException("Error creating source database from bundled asset", e) // Critical failure
            }
        }
        
        Log.d(TAG, "Attempting to open internal encrypted database: ${dbFile.path} with key: '$DATABASE_ENCRYPTION_KEY'")
        try {
            // Open (or create if copy failed and file still doesn't exist) the database with the key
            return SQLiteDatabase.openOrCreateDatabase(dbFile, DATABASE_ENCRYPTION_KEY, null)
        } catch (e: SQLiteException) { // Catches net.sqlcipher.database.SQLiteException
            Log.e(TAG, "SQLCipher Error opening/creating database. Path: ${dbFile.absolutePath}, Key: '$DATABASE_ENCRYPTION_KEY'. Message: ${e.message}", e)
            // This error specifically "file is not a database" with SQLCipher usually means:
            // 1. Key is wrong.
            // 2. File is not actually encrypted with SQLCipher or is corrupted.
            // 3. SQLCipher native libs didn't load, so it's trying to open it as a plain SQLite file.
            // Since your Python script works, point 3 is the most suspect if the key is correct in code.

            // Attempt to delete and recopy as a last resort if the file might be corrupted
            // or wasn't copied correctly initially.
            Log.w(TAG, "Attempting to delete and recopy potentially corrupted DB: ${dbFile.name}")
            if (dbFile.exists()) {
                dbFile.delete()
            }
            try {
                copyInitialBundledDatabase(dbFile)
                Log.i(TAG, "Re-copied bundled DB. Retrying open...")
                return SQLiteDatabase.openOrCreateDatabase(dbFile, DATABASE_ENCRYPTION_KEY, null) // Retry open
            } catch (ioe: IOException) {
                 Log.e(TAG, "FATAL: Failed to recopy bundled DB after open error.", ioe)
                 throw RuntimeException("Failed to recover database after open error.", ioe)
            } catch (sqleRecopy: SQLiteException) {
                Log.e(TAG, "FATAL: Still failed to open DB with SQLCipher after recopy attempt.", sqleRecopy)
                throw RuntimeException("SQLCipher still failed to open DB after recopy. Check key and native libs.", sqleRecopy)
            }
        } catch (e: Exception) { // Catch any other unexpected errors during open
            Log.e(TAG, "Unexpected general error opening database.", e)
            throw RuntimeException("Unexpected error opening database", e)
        }
    }

    private fun copyInitialBundledDatabase(destinationDbFile: File) {
        context.assets.open(initialBundledAssetPath).use { inputStream ->
            destinationDbFile.parentFile?.mkdirs() 
            FileOutputStream(destinationDbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                Log.i(TAG, "Initial bundled encrypted database '$initialBundledAssetDbName' copied to '${destinationDbFile.name}'")
            }
        }
    }

    fun replaceDatabase(newEncryptedDbFile: File): Boolean {
        val internalDbFile = getInternalDatabaseFile()
        Log.i(TAG, "Attempting to replace internal DB '${internalDbFile.name}' with new DB from '${newEncryptedDbFile.name}'")
        try {
            if (internalDbFile.exists()) {
                try {
                    val tempDb = SQLiteDatabase.openDatabase(internalDbFile.path, DATABASE_ENCRYPTION_KEY, null, SQLiteDatabase.OPEN_READWRITE)
                    tempDb?.close()
                } catch (e: Exception) {
                    Log.w(TAG,"Minor issue trying to probe/close old DB before replacement: ${e.message}")
                }

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

    // --- Data Access Methods ---
    // Your existing data access methods (getAllCategories, getNotes, etc.) are correct
    // and do not need to change as they operate on the SQLiteDatabase instance.
    // The key is that this instance is now an SQLCipher one.

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
