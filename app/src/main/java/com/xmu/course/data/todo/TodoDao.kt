package com.xmu.course.data.todo

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xmu.course.data.todo.model.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todo_items SET completed = :completed, updatedTime = :updatedTime WHERE id = :id")
    suspend fun updateCompletedById(id: Long, completed: Boolean, updatedTime: Long)

    @Query(
        "SELECT * FROM todo_items " +
            "ORDER BY completed ASC, " +
            "CASE WHEN deadline IS NULL THEN 1 ELSE 0 END ASC, " +
            "deadline ASC, updatedTime DESC",
    )
    suspend fun getAll(): List<TodoEntity>

    @Query(
        "SELECT * FROM todo_items " +
            "ORDER BY completed ASC, " +
            "CASE WHEN deadline IS NULL THEN 1 ELSE 0 END ASC, " +
            "deadline ASC, updatedTime DESC",
    )
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("DELETE FROM todo_items")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM todo_items " +
            "WHERE source = :source " +
            "AND deadline IS NOT NULL AND deadline < :baselineAt",
    )
    suspend fun deleteTronClassTodosBefore(source: String, baselineAt: Long)

    @Query(
        "DELETE FROM todo_items WHERE source = :source AND courseId IN (:courseIds)",
    )
    suspend fun deleteTronClassTodosForCourses(source: String, courseIds: List<Long>)

    @Query("DELETE FROM todo_items WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM todo_items WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TodoEntity?

    @Query(
        "SELECT * FROM todo_items WHERE source = :source AND externalId = :externalId LIMIT 1",
    )
    suspend fun getBySourceAndExternalId(source: String, externalId: String): TodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    /** 外部待办幂等写入；保留用户已有完成状态和创建时间。 */
    @Transaction
    suspend fun upsertExternalTodos(todos: List<TodoEntity>) {
        todos.forEach { incoming ->
            val existing = incoming.externalId?.let {
                getBySourceAndExternalId(incoming.source, it)
            }
            if (existing == null) {
                insert(incoming)
            } else {
                update(
                    incoming.copy(
                        id = existing.id,
                        completed = existing.completed,
                        createdTime = existing.createdTime,
                    ),
                )
            }
        }
    }
}
