package com.xmu.course.data.tronclass.assignment

import androidx.room.Entity
import androidx.room.PrimaryKey

/** TronClass 待办导入的持久化元数据；整张表只有 id=1 这一行。 */
@Entity(tableName = "tron_todo_sync_metadata")
data class TronTodoSyncMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val importBaselineAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
