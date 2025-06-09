package bankwiser.bankpromotion.material.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM Categories ORDER BY category_name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    // Add this new method to check if the table is empty
    @Query("SELECT COUNT(*) FROM Categories")
    suspend fun count(): Int
}
