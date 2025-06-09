package bankwiser.bankpromotion.material.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import bankwiser.bankpromotion.material.data.local.ContentDatabase
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import java.io.File
import java.io.FileOutputStream

class ContentRepository(private val db: ContentDatabase, private val context: Context) {

    private val prefs = context.getSharedPreferences("BankWiserPrefs", Context.MODE_PRIVATE)

    // Standard data access functions
    fun getAllCategories() = db.categoryDao().getAllCategories()
    fun getSubCategories(categoryId: String) = db.subCategoryDao().getSubCategoriesForCategory(categoryId)
    fun getNotes(subCategoryId: String) = db.noteDao().getNotesForSubCategory(subCategoryId)
    fun getNote(noteId: String) = db.noteDao().getNoteById(noteId)

    /**
     * Checks if data has been imported. If not, it performs a one-time import
     * from the asset database into the Room database.
     */
    suspend fun checkAndPrepopulate() {
        val isPopulated = prefs.getBoolean("db_populated_v1", false)
        if (!isPopulated) {
            val assetDbFile = extractAssetDb(context) ?: return
            val assetDb = SQLiteDatabase.openDatabase(assetDbFile.path, null, SQLiteDatabase.OPEN_READONLY)

            importCategories(assetDb, db)
            importSubCategories(assetDb, db)
            importNotes(assetDb, db)

            assetDb.close()
            assetDbFile.delete() // Clean up the temp file

            prefs.edit {
                putBoolean("db_populated_v1", true)
            }
        }
    }

    private suspend fun importCategories(assetDb: SQLiteDatabase, roomDb: ContentDatabase) {
        val cursor = assetDb.rawQuery("SELECT * FROM Categories", null)
        val categories = mutableListOf<CategoryEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val idIndex = it.getColumnIndex("category_id")
                val nameIndex = it.getColumnIndex("category_name")
                if (idIndex != -1) {
                    val id = it.getString(idIndex)
                    if (id != null) {
                        val name = if (nameIndex != -1) it.getString(nameIndex) else null
                        categories.add(CategoryEntity(categoryId = id, categoryName = name))
                    }
                }
            }
        }
        roomDb.categoryDao().insertAll(categories)
    }

    private suspend fun importSubCategories(assetDb: SQLiteDatabase, roomDb: ContentDatabase) {
        val cursor = assetDb.rawQuery("SELECT * FROM SubCategories", null)
        val subCategories = mutableListOf<SubCategoryEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val idIndex = it.getColumnIndex("sub_category_id")
                val catIdIndex = it.getColumnIndex("category_id")
                val nameIndex = it.getColumnIndex("sub_category_name")
                if (idIndex != -1 && catIdIndex != -1) {
                    val id = it.getString(idIndex)
                    val catId = it.getString(catIdIndex)
                    if (id != null && catId != null) {
                        val name = if (nameIndex != -1) it.getString(nameIndex) else null
                        subCategories.add(SubCategoryEntity(subCategoryId = id, categoryId = catId, subCategoryName = name))
                    }
                }
            }
        }
        roomDb.subCategoryDao().insertAll(subCategories)
    }

    private suspend fun importNotes(assetDb: SQLiteDatabase, roomDb: ContentDatabase) {
        val cursor = assetDb.rawQuery("SELECT * FROM Notes", null)
        val notes = mutableListOf<NoteEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val idIndex = it.getColumnIndex("note_id")
                val subCatIdIndex = it.getColumnIndex("sub_category_id")
                val titleIndex = it.getColumnIndex("title")
                val bodyIndex = it.getColumnIndex("body")
                if (idIndex != -1) {
                    val id = it.getString(idIndex)
                    if (id != null) {
                        val subCatId = if (subCatIdIndex != -1) it.getString(subCatIdIndex) else null
                        val title = if (titleIndex != -1) it.getString(titleIndex) else null
                        val body = if (bodyIndex != -1) it.getString(bodyIndex) else null
                        notes.add(NoteEntity(noteId = id, subCategoryId = subCatId, title = title, body = body))
                    }
                }
            }
        }
        roomDb.noteDao().insertAll(notes)
    }

    private fun extractAssetDb(context: Context): File? {
        val dbFile = File(context.cacheDir, "content_v1.db")
        try {
            context.assets.open("database/content_v1.db").use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return dbFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
