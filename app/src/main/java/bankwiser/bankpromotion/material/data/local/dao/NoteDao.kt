package bankwiser.bankpromotion.material.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import bankwiser.bankpromotion.material.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM Notes WHERE sub_category_id = :subCategoryId")
    fun getNotesForSubCategory(subCategoryId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM Notes WHERE note_id = :noteId LIMIT 1")
    fun getNoteById(noteId: String): Flow<NoteEntity?>
}
