package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoUiMapperTest {
    @Test
    fun `TodoEntity 映射为展示模型并隐藏持久化字段`() {
        val entity = TodoEntity(
            id = 7L,
            title = "完成实验报告",
            description = "整理实验数据",
            courseId = 12L,
            source = "TRONCLASS",
            deadline = 1_700_000_000_000L,
            completed = false,
            createdTime = 10L,
            updatedTime = 20L,
            externalId = "homework-7",
        )

        assertEquals(
            TodoUiModel(
                id = 7L,
                title = "完成实验报告",
                description = "整理实验数据",
                courseId = 12L,
                source = TodoFeatureSource.EXTERNAL,
                deadline = 1_700_000_000_000L,
                completed = false,
            ),
            entity.toUiModel(),
        )
    }
}
