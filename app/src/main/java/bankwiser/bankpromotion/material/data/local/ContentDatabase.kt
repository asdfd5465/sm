package bankwiser.bankpromotion.material.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import bankwiser.bankpromotion.material.data.local.dao.CategoryDao
import bankwiser.bankpromotion.material.data.local.dao.NoteDao
import bankwiser.bankpromotion.material.data.local.dao.SubCategoryDao
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        fun getDatabase(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContentDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(PrepopulateCallback(context))
                .build()

                INSTANCE = instance
                instance
            }
        }
    }

    private class PrepopulateCallback(private val context: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                prePopulate(context, getDatabase(context))
            }
        }

        private suspend fun prePopulate(context: Context, instance: ContentDatabase) {
            val assetDbFile = extractAssetDb(context) ?: return

            val assetDb = SQLiteDatabase.openDatabase(assetDbFile.path, null, SQLiteDatabase.OPEN_READONLY)

            // Populate Categories
            val categoryCursor = assetDb.rawQuery("SELECT * FROM Categories", null)
            val categories = mutableListOf<CategoryEntity>()
            while (categoryCursor.moveToNext()) {
                val idIndex = categoryCursor.getColumnIndex("category_id")
                val nameIndex = categoryCursor.getColumnIndex("category_name")
                if (idIndex != -1) {
                    val id = categoryCursor.getString(idIndex)
                    // We must have a non-null ID to proceed
                    if (id != null) {
                        val name = if (nameIndex != -1) categoryCursor.getString(nameIndex) else null
                        categories.add(CategoryEntity(categoryId = id, categoryName = name))
                    }
                }
            }
            categoryCursor.close()
            instance.categoryDao().insertAll(categories)


            // Populate SubCategories
            val subCategoryCursor = assetDb.rawQuery("SELECT * FROM SubCategories", null)
            val subCategories = mutableListOf<SubCategoryEntity>()
            while (subCategoryCursor.moveToNext()) {
                val idIndex = subCategoryCursor.getColumnIndex("sub_category_id")
                val catIdIndex = subCategoryCursor.getColumnIndex("category_id")
                val nameIndex = subCategoryCursor.getColumnIndex("sub_category_name")
                if (idIndex != -1 && catIdIndex != -1) {
                    val id = subCategoryCursor.getString(idIndex)
                    val catId = subCategoryCursor.getString(catIdIndex)
                    if (id != null && catId != null) {
                        val name = if (nameIndex != -1) subCategoryCursor.getString(nameIndex) else null
                        subCategories.add(SubCategoryEntity(subCategoryId = id, categoryId = catId, subCategoryName = name))
                    }
                }
            }
            subCategoryCursor.close()
            instance.subCategoryDao().insertAll(subCategories)

            // Populate Notes
            val noteCursor = assetDb.rawQuery("SELECT * FROM Notes", null)
            val notes = mutableListOf<NoteEntity>()
            while (noteCursor.moveToNext()) {
                val idIndex = noteCursor.getColumnIndex("note_id")
                val subCatIdIndex = noteCursor.getColumnIndex("sub_category_id")
                val titleIndex = noteCursor.getColumnIndex("title")
                val bodyIndex = noteCursor.getColumnIndex("body")
                if (idIndex != -1) {
                    val id = noteCursor.getString(idIndex)
                    if(id != null) {
                        val subCatId = if (subCatIdIndex != -1) noteCursor.getString(subCatIdIndex) else null
                        val title = if (titleIndex != -1) noteCursor.getString(titleIndex) else null
                        val body = if (bodyIndex != -1) noteCursor.getString(bodyIndex) else null
                        notes.add(NoteEntity(noteId = id, subCategoryId = subCatId, title = title, body = body))
                    }
                }
            }
            noteCursor.close()
            instance.noteDao().insertAll(notes)

            assetDb.close()
            assetDbFile.delete() // Clean up the temp file
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
                // Handle exceptions, e.g., file not found
                e.printStackTrace()
                return null
            }
        }
    }
}
