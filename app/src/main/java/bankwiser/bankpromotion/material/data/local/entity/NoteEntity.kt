package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Reflects the new DB schema for Phase 2
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    // sub_category_id can be null in your new schema
    @ColumnInfo(name = "sub_category_id") val subCategoryId: String?
)
