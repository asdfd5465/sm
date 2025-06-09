package bankwiser.bankpromotion.material.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import bankwiser.bankpromotion.material.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM Categories ORDER BY category_name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
}
