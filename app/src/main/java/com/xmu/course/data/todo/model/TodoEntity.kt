package com.xmu.course.data.todo.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Todo 的来源。LOCAL 表示用户创建，TRONCLASS 表示畅课作业导入。 */
enum class TodoSource {
    LOCAL,
    TRONCLASS,
}

/**
 * 独立待办记录。
 *
 * courseId 与 source 共同构成逻辑关联：LOCAL 指向 CourseEntity.id，TRONCLASS
 * 指向 TronClass 的稳定远端课程 ID。由于它是多态引用，不能声明 Room 外键。
 */
@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["completed", "deadline"]),
        Index(value = ["source", "courseId"]),
        Index(value = ["source", "externalId"], unique = true),
    ],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val courseId: Long?,
    val source: String = TodoSource.LOCAL.name,
    val deadline: Long?,
    val completed: Boolean = false,
    val createdTime: Long,
    val updatedTime: Long,
    /** 外部来源的稳定标识；本地待办为 null，畅课作业为 homework activity id。 */
    val externalId: String? = null,
)
