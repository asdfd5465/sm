package bankwiser.bankpromotion.material.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import bankwiser.bankpromotion.material.data.local.entity.SubCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubCategoryDao {
    @Query("SELECT * FROM SubCategories WHERE category_id = :categoryId ORDER BY sub_category_name ASC")
    fun getSubCategoriesForCategory(categoryId: String): Flow<List<SubCategoryEntity>>
}
