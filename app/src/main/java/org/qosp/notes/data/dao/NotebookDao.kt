package org.qosp.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.Notebook

@Dao
interface NotebookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: Notebook): Long

    @Delete
    suspend fun delete(vararg notebooks: Notebook)

    @Query("DELETE FROM notebooks WHERE id = :id AND NOT EXISTS (SELECT 1 FROM notes WHERE notebookId = :id)")
    suspend fun deleteIfEmpty(id: Long)

    @Update
    suspend fun update(vararg notebooks: Notebook)

    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    fun getById(notebookId: Long): Flow<Notebook?>

    @Query("SELECT * FROM notebooks")
    fun getAll(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE notebookName = :name LIMIT 1")
    fun getByName(name: String): Flow<Notebook?>
}
