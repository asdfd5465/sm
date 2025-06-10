package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subcategories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["category_id"],
        childColumns = ["category_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["category_id"])]
)
data class SubCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "sub_category_id") val subCategoryId: String,
    // Corrected: Changed from String? to String
    @ColumnInfo(name = "category_id") val categoryId: String,
    // Corrected: Changed from String? to String
    @ColumnInfo(name = "sub_category_name") val subCategoryName: String
)
