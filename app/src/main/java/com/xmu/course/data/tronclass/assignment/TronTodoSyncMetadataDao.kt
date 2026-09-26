package com.xmu.course.data.tronclass.assignment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TronTodoSyncMetadataDao {
    @Query("SELECT * FROM tron_todo_sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun get(): TronTodoSyncMetadataEntity?

    @Query("DELETE FROM tron_todo_sync_metadata")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tron_todo_sync_metadata")
    suspend fun countAll(): Int

    /** 返回 -1 表示已有 baseline，永不覆盖已有时间。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(metadata: TronTodoSyncMetadataEntity): Long
}
