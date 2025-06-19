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

const val DATABASE_ENCRYPTION_KEY = "bankwiser"

class DatabaseHelper(private val context: Context) {

    private val internalDbName = "content.db"
    // This MUST match the EXACT path within your app/src/main/assets/ directory
    private val initialBundledAssetPath = "database/content_v1_initial_bundled.db" 

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
            }
        }
    }

    private fun getInternalDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    private fun copyInitialBundledDatabase(destinationDbFile: File): Boolean {
        Log.i(TAG, "Attempting to copy initial bundled asset '$initialBundledAssetPath' to '${destinationDbFile.absolutePath}'")
        try {
            context.assets.open(initialBundledAssetPath).use { inputStream ->
                destinationDbFile.parentFile?.mkdirs() // Ensure directory exists
                FileOutputStream(destinationDbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    Log.i(TAG, "Successfully copied initial bundled encrypted database '$initialBundledAssetPath' to '${destinationDbFile.name}'. File exists: ${destinationDbFile.exists()}")
                    return destinationDbFile.exists() && destinationDbFile.length() > 0
                }
            }
        } catch (ioe: IOException) {
            Log.e(TAG, "IOException during copyInitialBundledDatabase for asset '$initialBundledAssetPath' to '${destinationDbFile.name}'", ioe)
            // If it's a FileNotFoundException for the asset, that's a critical packaging error.
            if (ioe is java.io.FileNotFoundException && ioe.message?.contains(initialBundledAssetPath, ignoreCase = true) == true) {
                Log.e(TAG, "CRITICAL: Bundled asset DB file NOT FOUND at path: $initialBundledAssetPath. Check your app/src/main/assets structure.")
            }
            return false // Indicate copy failure
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during copyInitialBundledDatabase", e)
            return false // Indicate copy failure
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        ensureSqlCipherLoaded(context.applicationContext)
        val dbFile = getInternalDatabaseFile()
        var dbJustCopied = false

        if (!dbFile.exists()) {
            Log.d(TAG, "Internal DB '${dbFile.name}' not found. Attempting to copy initial bundled version.")
            if (!copyInitialBundledDatabase(dbFile)) {
                // If copy fails, dbFile will still not exist.
                Log.e(TAG, "FATAL: Failed to copy initial bundled database. Cannot open database.")
                throw RuntimeException("Initial DB copy failed, cannot proceed to open database.")
            }
            dbJustCopied = true // Flag that we just copied it
        }

        Log.d(TAG, "Attempting to open DB: ${dbFile.path} with key: '$DATABASE_ENCRYPTION_KEY'")
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, null)
            Log.i(TAG, "DB opened with empty key to set PRAGMAs.")

            db.execSQL("PRAGMA cipher_compatibility = 3;")
            Log.i(TAG, "Executed: PRAGMA cipher_compatibility = 3;")

            val keyStatement = "PRAGMA key = ?;"
            db.execSQL(keyStatement, arrayOf<Any>(DATABASE_ENCRYPTION_KEY))
            Log.i(TAG, "PRAGMA key = '$DATABASE_ENCRYPTION_KEY' executed via execSQL.")

            db.rawQuery("SELECT count(*) FROM sqlite_master;", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) > 0) { // Check for positive count
                    Log.i(TAG, "SQLCipher DB opened and keyed successfully. sqlite_master count: ${cursor.getInt(0)}")
                } else {
                    Log.e(TAG, "Verification query (sqlite_master count) failed or returned zero after keying. DB might be empty or key/compatibility wrong.")
                    throw SQLiteException("Verification query failed (sqlite_master count ${cursor.getInt(0)}).")
                }
            }
            return db
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLCipher Error during open/keying process: ${e.message}. Path: ${dbFile.absolutePath}", e)
            db?.close()

            // If it was a freshly copied DB and it still fails, the bundled asset or key is the problem.
            if (dbJustCopied) {
                Log.e(TAG, "FATAL: SQLCipher failed to open DB even after a fresh asset copy. Check bundled asset ('$initialBundledAssetPath') encryption and key ('$DATABASE_ENCRYPTION_KEY').", e)
                throw RuntimeException("SQLCipher failed to open DB after fresh asset copy. Key or bundled asset is likely incorrect.", e)
            } else if (dbFile.exists()){ 
                // If it wasn't just copied and failed, it might be an old corrupted internal DB. Try deleting and re-copying ONCE.
                Log.w(TAG, "Open/keying failed on existing internal DB. Deleting and attempting recopy from asset ONCE.")
                dbFile.delete()
                // Recursive call implies isRetryAttempt was removed, the dbJustCopied handles the "once" logic
                return openDatabase() // This will trigger copyInitialBundledDatabase again
            }
            // If dbFile didn't exist and copy failed before this point, that throw would have happened.
            // If it's some other SQLiteException not related to a fresh copy failure.
            throw RuntimeException("Unhandled SQLCipher open/key failure.", e)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected general error during database open/keying process.", e)
            db?.close()
            throw RuntimeException("Unexpected error opening database", e)
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
            Log.e(TAG, "SQLCipher Query Exception during readData: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "General DB Read Exception during readData: ${e.message}", e)
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

    // --- Data Access Methods (remain unchanged) ---
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
