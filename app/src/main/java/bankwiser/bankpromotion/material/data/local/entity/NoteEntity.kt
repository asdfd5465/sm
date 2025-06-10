package bankwiser.bankpromotion.material.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Reflects the new DB schema for Phase 2
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: String,
    // Corrected: Changed from String? to String
    @ColumnInfo(name = "title") val title: String,
    // Corrected: Changed from String? to String
    @ColumnInfo(name = "body") val body: String,
    // This remains nullable as it is in your DB schema
    @ColumnInfo(name = "sub_category_id") val subCategoryId: String?
)
