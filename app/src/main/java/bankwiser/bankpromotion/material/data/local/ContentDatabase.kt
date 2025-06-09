package bankwiser.bankpromotion.material.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import bankwiser.bankpromotion.material.data.local.dao.CategoryDao
import bankwiser.bankpromotion.material.data.local.dao.NoteDao
import bankwiser.bankpromotion.material.data.local.dao.SubCategoryDao
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity

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

        fun getDatabase(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContentDatabase::class.java,
                    DATABASE_NAME
                )
                .createFromAsset(ASSET_DB_PATH)
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
