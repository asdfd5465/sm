package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.Cursor
import android.util.Log
import bankwiser.bankpromotion.material.data.model.*
import net.sqlcipher.database.SQLiteDatabase // SQLCipher import
import net.sqlcipher.database.SQLiteException // SQLCipher import
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// IMPORTANT: This key is embedded for now.
// For production, use a more secure key management strategy (NDK, server, etc.)
const val DATABASE_ENCRYPTION_KEY = "your-super-secret-passphrase-for-db" // REPLACE THIS!

class DatabaseHelper(private val context: Context) {

    private val internalDbName = "content.db" // This will be the SQLCipher encrypted DB
    private val initialBundledAssetDbName = "content_v1_bundled_encrypted.db" // Your initial encrypted DB in app/assets
    private val initialBundledAssetPath = "database/$initialBundledAssetDbName"

    companion object {
        private const val TAG = "DatabaseHelper"
        var isDatabaseLoaded = false // Flag to ensure SQLCipher libraries are loaded once
    }

    init {
        synchronized(DatabaseHelper::class.java) {
            if (!isDatabaseLoaded) {
                SQLiteDatabase.loadLibs(context)
                isDatabaseLoaded = true
                Log.d(TAG, "SQLCipher libraries loaded.")
            }
        }
    }

    private fun getDatabaseFile(): File {
        return context.getDatabasePath(internalDbName)
    }

    // Opens (and creates if not exists) the internal encrypted database
    private fun openDatabase(): SQLiteDatabase {
        val dbFile = getDatabaseFile()
        if (!dbFile.exists()) {
            try {
                Log.d(TAG, "Internal database does not exist. Copying initial bundled encrypted DB.")
                copyInitialBundledDatabase(dbFile)
            } catch (e: IOException) {
                Log.e(TAG, "Error copying initial bundled database", e)
                throw RuntimeException("Error creating source database", e)
            }
        }
        Log.d(TAG, "Opening internal encrypted database: ${dbFile.path}")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, DATABASE_ENCRYPTION_KEY, null)
    }

    private fun copyInitialBundledDatabase(dbFile: File) {
        // This copies the *initial* encrypted DB bundled with the APK's assets
        // This is for the very first install scenario.
        // Subsequent updates will come from PAD.
        context.assets.open(initialBundledAssetPath).use { inputStream ->
            dbFile.parentFile?.mkdirs()
            FileOutputStream(dbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                Log.i(TAG, "Initial bundled encrypted database copied to ${dbFile.absolutePath}")
            }
        }
    }

    // This function will be called by AssetPackUpdateManager
    // to replace the internal DB with a new one from PAD
    fun replaceDatabase(newEncryptedDbFile: File): Boolean {
        val internalDb = getDatabaseFile()
        var oldDbConnection: SQLiteDatabase? = null
        try {
            // Ensure any existing connection is closed before replacing
            // This is tricky; ideally, the app ensures no active reads/writes.
            // For simplicity now, we just try to delete and copy.
            // A more robust solution might involve signaling all DB users to release connections.
            if (internalDb.exists()) {
                // Try to open and close it to ensure it's not locked, though this isn't foolproof
                try {
                    oldDbConnection = SQLiteDatabase.openDatabase(internalDb.path, DATABASE_ENCRYPTION_KEY, null, SQLiteDatabase.OPEN_READWRITE)
                    oldDbConnection?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open/close old DB before replacement: ${e.message}")
                }
                if (!internalDb.delete()) {
                    Log.e(TAG, "Failed to delete old internal database.")
                    return false
                }
                Log.d(TAG, "Old internal database deleted.")
            }

            newEncryptedDbFile.copyTo(internalDb, overwrite = true)
            Log.i(TAG, "New encrypted database from PAD copied to ${internalDb.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing database with PAD version", e)
            // If replacement fails, try to restore the old one if possible, or handle error
            return false
        }
    }


    private inline fun <T> readData(queryBlock: (SQLiteDatabase) -> T): T {
        val db = openDatabase() // Opens with SQLCipher
        try {
            return queryBlock(db)
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLCipher Query Exception", e)
            throw e // Re-throw to be caught by ViewModel or calling code
        } catch (e: Exception) {
            Log.e(TAG, "General DB Read Exception", e)
            throw e
        }
        finally {
            db.close()
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index != -1 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index != -1 && !isNull(index)) getInt(index) else null
    }

    private fun Cursor.getBoolean(columnName: String): Boolean {
        val index = getColumnIndex(columnName)
        return if (index != -1 && !isNull(index)) getInt(index) == 1 else false
    }

    // --- Data Access Methods (Queries remain the same, but now operate on encrypted DB) ---
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

    // --- Search Functions ---
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
