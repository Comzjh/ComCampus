package com.xmu.course.data.privacy

import androidx.room.withTransaction
import com.xmu.course.contracts.provider.PrivacyDataOwner
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.model.TodoSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * TronClass 同步数据的隐私所有者。
 *
 * 数据范围 = 第三方同步数据：source=TRONCLASS 的 todo_items、
 * tron_todo_sync_metadata、tron_courses。
 *
 * 删除安全模型（Phase 5.3.2-C3，最高风险点）：
 * - 只删 WHERE source = 'TRONCLASS' 的待办，用户创建的 LOCAL Todo 绝不受影响；
 * - source 通过 [TodoSource.TRONCLASS] 类型安全传入，不在 SQL 中硬编码字符串；
 * - 仅 Room DELETE，无网络、无同步、无认证清理；todo_items 无外键，
 *   tron_courses 删除不影响已存在的 todo 行（多态引用，无 FK）。
 */
class TronClassDataOwner(private val database: AppDatabase) : PrivacyDataOwner {

    override suspend fun countLocalData(): Int = coroutineScope {
        val todos = async { database.todoDao().countBySource(TodoSource.TRONCLASS.name) }
        val metadata = async { database.tronTodoSyncMetadataDao().countAll() }
        val courses = async { database.tronCourseDao().countAll() }
        todos.await() + metadata.await() + courses.await()
    }

    override suspend fun clearLocalData() {
        database.withTransaction {
            database.todoDao().deleteBySource(TodoSource.TRONCLASS.name)
            database.tronTodoSyncMetadataDao().deleteAll()
            database.tronCourseDao().deleteAll()
        }
    }
}
