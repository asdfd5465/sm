package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "SubCategories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["category_id"],
        childColumns = ["category_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SubCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "sub_category_id") val subCategoryId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "sub_category_name") val subCategoryName: String
)
