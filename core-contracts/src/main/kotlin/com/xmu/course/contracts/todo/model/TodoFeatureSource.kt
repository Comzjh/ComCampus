package com.xmu.course.contracts.todo.model

/** Todo Feature 可见的来源类别，不暴露具体 Provider、系统或存储实现。 */
enum class TodoFeatureSource {
    LOCAL,
    EXTERNAL,
}
