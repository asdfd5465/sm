package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import bankwiser.bankpromotion.material.data.local.dao.CategoryDao
import bankwiser.bankpromotion.material.data.local.dao.NoteDao
import bankwiser.bankpromotion.material.data.local.dao.SubCategoryDao
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import java.io.File
import java.io.FileOutputStream

@Database(
    entities = [CategoryEntity::class, SubCategoryEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ContentDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun subCategoryDao(): SubCategoryDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: ContentDatabase? = null
        private const val DATABASE_NAME = "content.db"
        private const val ASSET_DB_PATH = "database/content_v1.db"
        private const val ASSET_DB_FILENAME = "content_v1.db"
        private const val TAG = "ContentDatabase"

        fun getDatabase(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContentDatabase::class.java,
                    DATABASE_NAME
                )
                // The complex callback is GONE. We handle this elsewhere.
                .build()
                INSTANCE = instance
                instance
            }
        }

        // This is a new public function to handle the data import.
        suspend fun prePopulateFromAsset(context: Context, database: ContentDatabase) {
            Log.d(TAG, "Starting data import from asset DB.")
            val assetDbFile = extractAssetDb(context) ?: run {
                Log.e(TAG, "Failed to extract asset DB.")
                return
            }
            val assetDb = SQLiteDatabase.openDatabase(assetDbFile.path, null, SQLiteDatabase.OPEN_READONLY)

            // Populate Categories
            val categoryCursor = assetDb.rawQuery("SELECT * FROM Categories", null)
            val categories = mutableListOf<CategoryEntity>()
            categoryCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val idIndex = cursor.getColumnIndex("category_id")
                    val nameIndex = cursor.getColumnIndex("category_name")
                    if (idIndex != -1) {
                        val id = cursor.getString(idIndex)
                        if (id != null) {
                            val name = if (nameIndex != -1 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                            categories.add(CategoryEntity(categoryId = id, categoryName = name))
                        }
                    }
                }
            }
            database.categoryDao().insertAll(categories)
            Log.d(TAG, "Imported ${categories.size} categories.")

            // Populate SubCategories
            val subCategoryCursor = assetDb.rawQuery("SELECT * FROM SubCategories", null)
            val subCategories = mutableListOf<SubCategoryEntity>()
            subCategoryCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val idIndex = cursor.getColumnIndex("sub_category_id")
                    val catIdIndex = cursor.getColumnIndex("category_id")
                    val nameIndex = cursor.getColumnIndex("sub_category_name")
                    if (idIndex != -1 && catIdIndex != -1) {
                        val id = cursor.getString(idIndex)
                        val catId = cursor.getString(catIdIndex)
                        if (id != null && catId != null) {
                            val name = if (nameIndex != -1 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                            subCategories.add(SubCategoryEntity(subCategoryId = id, categoryId = catId, subCategoryName = name))
                        }
                    }
                }
            }
            database.subCategoryDao().insertAll(subCategories)
            Log.d(TAG, "Imported ${subCategories.size} sub-categories.")

            // Populate Notes
            val noteCursor = assetDb.rawQuery("SELECT * FROM Notes", null)
            val notes = mutableListOf<NoteEntity>()
            noteCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val idIndex = cursor.getColumnIndex("note_id")
                    val subCatIdIndex = cursor.getColumnIndex("sub_category_id")
                    val titleIndex = cursor.getColumnIndex("title")
                    val bodyIndex = cursor.getColumnIndex("body")
                    if (idIndex != -1) {
                        val id = cursor.getString(idIndex)
                        if(id != null) {
                            val subCatId = if (subCatIdIndex != -1 && !cursor.isNull(subCatIdIndex)) cursor.getString(subCatIdIndex) else null
                            val title = if (titleIndex != -1 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
                            val body = if (bodyIndex != -1 && !cursor.isNull(bodyIndex)) cursor.getString(bodyIndex) else null
                            notes.add(NoteEntity(noteId = id, subCategoryId = subCatId, title = title, body = body))
                        }
                    }
                }
            }
            database.noteDao().insertAll(notes)
            Log.d(TAG, "Imported ${notes.size} notes.")

            assetDb.close()
            assetDbFile.delete()
            Log.d(TAG, "Data import finished. Temp asset DB deleted.")
        }

        private fun extractAssetDb(context: Context): File? {
            val dbFile = File(context.cacheDir, ASSET_DB_FILENAME)
            try {
                context.assets.open(ASSET_DB_PATH).use { inputStream ->
                    FileOutputStream(dbFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return dbFile
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting asset DB", e)
                return null
            }
        }
    }
}
