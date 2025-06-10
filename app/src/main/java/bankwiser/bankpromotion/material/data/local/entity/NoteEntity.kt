package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// This entity now perfectly matches the 'notes' table in your DB file.
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "note_type") val noteType: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "sub_category_id") val subCategoryId: String?,
    @ColumnInfo(name = "tag_id") val tagId: String?,
    @ColumnInfo(name = "is_free_launch_content") val isFreeLaunchContent: Boolean,
    @ColumnInfo(name = "is_premium") val isPremium: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean
)
