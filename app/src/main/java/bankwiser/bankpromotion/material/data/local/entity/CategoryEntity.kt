package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey @ColumnInfo(name = "category_id") val categoryId: String,
    // Corrected: Changed from String? to String to match NOT NULL constraint
    @ColumnInfo(name = "category_name") val categoryName: String
)
