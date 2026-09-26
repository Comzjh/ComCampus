package com.xmu.course.data.todo

import com.xmu.course.data.local.CourseDao
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.data.tronclass.repository.TronCourseDao
import com.xmu.course.domain.todo.TodoDeadlinePolicy
import com.xmu.course.domain.todo.TodoDatePolicy
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TodoCourseOption(
    val id: Long,
    val name: String,
    val teacher: String,
    val source: TodoSource,
)

interface TodoRepositoryContract {
    fun observeTodos(): Flow<List<TodoEntity>>
    suspend fun addTodo(title: String, description: String, deadline: Long?, courseId: Long?, source: TodoSource): Long
    suspend fun updateTodo(command: UpdateTodoCommand)
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun deleteTodo(id: Long)
    suspend fun getCourseOptions(): List<TodoCourseOption>
}

/** 待办数据仓库；UI 不直接接触 Room DAO。 */
class TodoRepository(
    private val todoDao: TodoDao,
    private val courseDao: CourseDao? = null,
    private val tronCourseDao: TronCourseDao? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TodoRepositoryContract {

    override fun observeTodos(): Flow<List<TodoEntity>> = todoDao.observeAll().map(::sortTodos)

    suspend fun getAll(): List<TodoEntity> = sortTodos(todoDao.getAll())

    override suspend fun addTodo(
        title: String,
        description: String,
        deadline: Long?,
        courseId: Long?,
        source: TodoSource,
    ): Long {
        require(title.isNotBlank()) { "待办标题不能为空" }
        val now = clock()
        return todoDao.insert(
            TodoEntity(
                title = title.trim(),
                description = description.trim(),
                courseId = courseId,
                source = if (courseId == null) TodoSource.LOCAL.name else source.name,
                deadline = deadline,
                createdTime = now,
                updatedTime = now,
            ),
        )
    }

    override suspend fun updateTodo(command: UpdateTodoCommand) {
        require(command.title.isNotBlank()) { "待办标题不能为空" }
        val existing = todoDao.getById(command.id) ?: return
        todoDao.update(TodoUpdateMapper.applyTo(existing, command, updatedTime = clock()))
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        todoDao.updateCompletedById(id, completed, clock())
    }

    override suspend fun deleteTodo(id: Long) = todoDao.deleteById(id)

    /** 课程选择器使用的运行时目录，不会给 Todo 建立数据库外键。 */
    override suspend fun getCourseOptions(): List<TodoCourseOption> {
        val local = courseDao?.getAll().orEmpty().map {
            TodoCourseOption(it.id, it.name, it.teacher, TodoSource.LOCAL)
        }
        val tron = tronCourseDao?.getAll().orEmpty().map {
            // TRONCLASS 使用稳定远端 ID，避免课程缓存刷新后关联失效。
            TodoCourseOption(it.tronCourseId, it.name, it.instructor, TodoSource.TRONCLASS)
        }
        return (local + tron).distinctBy { it.source to it.id }.sortedWith(
            compareBy<TodoCourseOption> { it.name }.thenBy { it.source.name }.thenBy { it.id },
        )
    }

    companion object {
        /** 逾期是运行时状态，不落库；手动和畅课待办均按各自已有记录判断。 */
        fun isOverdue(
            todo: TodoEntity,
            nowMillis: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): Boolean = !todo.completed && TodoDeadlinePolicy.isOverdue(
            todo.deadline?.let {
                TodoDatePolicy.effectiveDeadline(
                    deadlineMillis = it,
                    zoneId = zoneId,
                    isDateOnly = todo.source == TodoSource.LOCAL.name,
                )
            },
            nowMillis,
        )

        /** 未完成优先；有截止时间优先；同截止时间按最近更新时间倒序。 */
        fun sortTodos(
            todos: List<TodoEntity>,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): List<TodoEntity> = todos.sortedWith(
            compareBy<TodoEntity> { it.completed }
                .thenBy { todo -> todo.deadline == null }
                .thenBy { todo ->
                    todo.deadline?.let { deadline -> effectiveDeadline(todo, deadline, zoneId) } ?: Long.MAX_VALUE
                }
                .thenByDescending { it.updatedTime },
        )

        private fun effectiveDeadline(todo: TodoEntity, deadline: Long, zoneId: ZoneId): Long =
            TodoDatePolicy.effectiveDeadline(
                deadlineMillis = deadline,
                zoneId = zoneId,
                isDateOnly = todo.source == TodoSource.LOCAL.name,
            )
    }
}
