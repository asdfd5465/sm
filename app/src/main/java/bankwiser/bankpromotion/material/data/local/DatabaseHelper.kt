package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.Cursor
import android.util.Log
import bankwiser.bankpromotion.material.data.model.*
import net.sqlcipher.database.SQLiteDatabase
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
        var isSqlCipherLoaded = false
    }

    init {
        ensureSqlCipherLoaded()
    }

    private fun ensureSqlCipherLoaded() {
        synchronized(DatabaseHelper::class.java) {
            if (!isSqlCipherLoaded) {
                try {
                    SQLiteDatabase.loadLibs(context.applicationContext)
                    isSqlCipherLoaded = true
                    Log.i(TAG, "SQLCipher libraries loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "CRITICAL: UnsatisfiedLinkError loading SQLCipher.", e)
                    throw RuntimeException("Failed to load SQLCipher native libraries", e)
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: An unexpected error occurred loading SQLCipher libraries.", e)
                    throw RuntimeException("Unexpected error loading SQLCipher libraries", e)
                }
            }
        }
    }

    private fun getInternalDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    private fun openDatabase(isInitialCopyAttempt: Boolean = false): SQLiteDatabase {
        ensureSqlCipherLoaded()
        val dbFile = getInternalDatabaseFile()

        if (!dbFile.exists() && !isInitialCopyAttempt) { // Avoid recursive copy if initial copy itself fails
            Log.d(TAG, "Internal DB '${dbFile.name}' not found. Copying initial bundled version.")
            try {
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "FATAL: Error copying initial bundled DB.", e)
                throw RuntimeException("Error creating source DB from asset", e)
            }
        } else if (!dbFile.exists() && isInitialCopyAttempt) {
            // This means copyInitialBundledDatabase was called, it failed, and we are trying again.
            // This shouldn't happen if copyInitialBundledDatabase throws an exception.
             Log.e(TAG, "FATAL: Initial DB copy seems to have failed, and file still doesn't exist.")
             throw RuntimeException("Initial DB copy failed, cannot open.")
        }


        Log.d(TAG, "Attempting to open DB: ${dbFile.path} with key: '$DATABASE_ENCRYPTION_KEY'")
        var db: SQLiteDatabase? = null
        try {
            // Step 1: Open the database file without a key first, or with an empty key
            // This is necessary to set PRAGMAs before keying for SQLCipher 4 opening a v3 DB.
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null) // Empty key initially

            // Step 2: Set SQLCipher 3 compatibility PRAGMA
            // Other PRAGMAs like kdf_iter, hmac_pgno, etc., might be needed if 'cipher_compatibility' isn't enough,
            // but this is the primary one.
            db.execSQL("PRAGMA cipher_compatibility = 3;")
            Log.i(TAG, "PRAGMA cipher_compatibility = 3 executed.")

            // Step 3: Provide the actual key
            db.execSQL("PRAGMA key = '$DATABASE_ENCRYPTION_KEY';")
            Log.i(TAG, "PRAGMA key provided.")

            // Step 4: Verify decryption by trying a simple query
            db.rawQuery("SELECT count(*) FROM sqlite_master;", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    Log.i(TAG, "SQLCipher DB opened successfully. sqlite_master count: $count")
                } else {
                    throw SQLiteException("Verification query failed after keying (no rows in sqlite_master count).")
                }
            }
            return db
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLCipher Error during open/keying: ${e.message}. Path: ${dbFile.absolutePath}", e)
            db?.close() // Ensure DB is closed if an error occurs during this process

            // If this is the first attempt after a fresh copy (or potential recopy) and it fails,
            // it's a more severe issue (wrong key, truly corrupt file, or SQLCipher lib load issue).
            if (isInitialCopyAttempt && dbFile.exists()) {
                 Log.e(TAG, "SQLCipher failed to open DB even after ensuring it's a fresh copy from assets. Key or file is likely the issue.")
                 throw RuntimeException("SQLCipher failed to open DB after fresh asset copy. Key: '$DATABASE_ENCRYPTION_KEY', File: ${dbFile.name}", e)
            } else if (dbFile.exists() && !isInitialCopyAttempt) { // If it's not the initial copy attempt, try deleting and re-copying once.
                Log.w(TAG, "Attempting to delete and recopy potentially problematic DB: ${dbFile.name}")
                dbFile.delete()
                try {
                    copyInitialBundledDatabase(dbFile) // This function is now only for the initial bundled asset
                    Log.i(TAG, "Re-copied bundled DB. Retrying open with isInitialCopyAttempt=true...")
                    // Recursive call, but with a flag to prevent infinite loop on copy failure
                    return openDatabase(isInitialCopyAttempt = true) 
                } catch (ioe: IOException) {
                    Log.e(TAG, "FATAL: Failed to recopy bundled DB after open error.", ioe)
                    throw RuntimeException("Failed to recover DB after open error.", ioe)
                }
            }
            throw RuntimeException("SQLCipher failed to open DB. Key: '$DATABASE_ENCRYPTION_KEY', File: ${dbFile.name}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected general error opening database.", e)
            db?.close()
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
                // No need to open/close here, just delete and copy.
                // Ensure no other part of the app is holding the DB open.
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
    // These remain unchanged from your working version.
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
