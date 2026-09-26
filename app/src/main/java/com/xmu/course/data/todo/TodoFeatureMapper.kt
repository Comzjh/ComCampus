package com.xmu.course.data.todo

import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource

/** 持久化模型到 Todo feature 模型的单向映射。 */
fun TodoEntity.toFeatureModel(): TodoFeatureModel = TodoFeatureModel(
    id = TodoFeatureId(id),
    title = title,
    description = description,
    courseReference = courseId?.let { value ->
        if (source == TodoSource.TRONCLASS.name) {
            TodoCourseReference.External(value)
        } else {
            TodoCourseReference.Local(value)
        }
    },
    source = source.toFeatureSource(),
    deadline = deadline,
    completed = completed,
)

/** Repository 查询结果到 Todo feature 课程选择模型的单向映射。 */
fun TodoCourseOption.toFeatureModel(): TodoCourseOptionModel = TodoCourseOptionModel(
    reference = if (source == TodoSource.TRONCLASS) {
        TodoCourseReference.External(id)
    } else {
        TodoCourseReference.Local(id)
    },
    name = name,
    teacher = teacher,
)

/** 将 feature 编辑输入转换为现有 data 写入命令；不携带持久化字段。 */
fun TodoFeatureModel.toUpdateCommand(
    title: String,
    description: String,
    deadline: Long?,
    courseId: Long?,
    source: TodoSource,
): UpdateTodoCommand = UpdateTodoCommand(
    id = id.value,
    title = title,
    description = description,
    deadline = deadline,
    courseId = courseId,
    source = source,
)

private fun String.toFeatureSource(): TodoFeatureSource =
    if (this == TodoSource.TRONCLASS.name) TodoFeatureSource.EXTERNAL else TodoFeatureSource.LOCAL
