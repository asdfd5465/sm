package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Categories")
data class CategoryEntity(
    // It's unusual for a Primary Key to be nullable, but this matches your DB schema.
    @PrimaryKey @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String?
)
