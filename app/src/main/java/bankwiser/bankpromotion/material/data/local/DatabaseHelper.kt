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
    private val initialBundledAssetDbName = "content_v1_initial_bundled.db"
    private val initialBundledAssetPath = "database/$initialBundledAssetDbName"

    companion object {
        private const val TAG = "DatabaseHelper"
        @Volatile
        private var isSqlCipherLoaded = false
        private val loadLock = Any()
    }

    init {
        ensureSqlCipherLoaded(context.applicationContext)
    }

    private fun ensureSqlCipherLoaded(appContext: Context) {
        synchronized(loadLock) {
            if (!isSqlCipherLoaded) {
                try {
                    Log.i(TAG, "Attempting to load SQLCipher libraries...")
                    SQLiteDatabase.loadLibs(appContext)
                    isSqlCipherLoaded = true
                    Log.i(TAG, "SQLCipher libraries loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "CRITICAL: UnsatisfiedLinkError loading SQLCipher.", e)
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

    private fun openDatabase(isRetryAttempt: Boolean = false): SQLiteDatabase {
        ensureSqlCipherLoaded(context.applicationContext)
        val dbFile = getInternalDatabaseFile()

        if (!dbFile.exists() && !isRetryAttempt) {
            Log.d(TAG, "Internal DB '${dbFile.name}' not found. Copying initial bundled version.")
            try {
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "FATAL: Error copying initial bundled DB.", e)
                throw RuntimeException("Error creating source DB from asset for path: ${dbFile.absolutePath}", e)
            }
        } else if (!dbFile.exists() && isRetryAttempt) {
             Log.e(TAG, "FATAL: Initial DB copy seems to have failed, and file still doesn't exist.")
             throw RuntimeException("Initial DB copy failed, cannot open.")
        }

        Log.d(TAG, "Attempting to open DB: ${dbFile.path} with key: '$DATABASE_ENCRYPTION_KEY'")
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, null) // Open with empty key, no hook needed here for PRAGMAs
            Log.i(TAG, "DB opened with empty key to set PRAGMAs.")

            db.execSQL("PRAGMA cipher_compatibility = 3;")
            Log.i(TAG, "Executed: PRAGMA cipher_compatibility = 3;")

            // CORRECTED: Use execSQL for PRAGMA key as it doesn't return a result set
            // and pass the key as an argument to prevent SQL injection (though less critical for PRAGMA key).
            // Or, more simply for PRAGMA key, using a direct string is common.
            // The main issue was calling .close() on a rawQuery for a PRAGMA that might not return a typical cursor.
            // Using execSQL is safer for PRAGMAs that modify state or don't return rows.
            val keyStatement = "PRAGMA key = ?;"
            db.execSQL(keyStatement, arrayOf<Any>(DATABASE_ENCRYPTION_KEY))
            Log.i(TAG, "PRAGMA key = '$DATABASE_ENCRYPTION_KEY' executed via execSQL.")
            
            // Alternative for PRAGMA key if execSQL with arguments causes issues with some SQLCipher versions/drivers:
            // db.execSQL("PRAGMA key = '$DATABASE_ENCRYPTION_KEY';") // Simpler, often works

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
            db?.close() 

            if (!isRetryAttempt && dbFile.exists()) {
                Log.w(TAG, "Open/keying failed. Deleting potentially corrupt/mismatched DB and retrying with fresh asset copy ONCE.")
                dbFile.delete()
                return openDatabase(isRetryAttempt = true)
            } else if (isRetryAttempt) {
                Log.e(TAG, "FATAL: SQLCipher still failed to open/key DB after deleting and recopying from assets. Key: '$DATABASE_ENCRYPTION_KEY'. This likely means the bundled asset is not correctly encrypted for this key/settings, or SQLCipher native libs are not working.", e)
                throw RuntimeException("SQLCipher open/key failed after retry with fresh asset. Check bundled DB encryption and native libs.", e)
            } else { 
                 Log.e(TAG, "FATAL: SQLCipher open/key failed and not a retry scenario. Key: '$DATABASE_ENCRYPTION_KEY'", e)
                throw RuntimeException("SQLCipher open/key failed. Check bundled DB and logs.", e)
            }
        } catch (e: Exception) {
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
            throw ioe
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
