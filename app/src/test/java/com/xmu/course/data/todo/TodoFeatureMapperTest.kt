package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoFeatureMapperTest {
    @Test
    fun `entity maps to feature model without persistence fields`() {
        val entity = TodoEntity(
            id = 7L,
            title = "复习",
            description = "章节一",
            courseId = 42L,
            source = TodoSource.TRONCLASS.name,
            deadline = 99L,
            completed = true,
            createdTime = 11L,
            updatedTime = 12L,
            externalId = "activity-7",
        )

        assertEquals(
            TodoFeatureModel(
                id = TodoFeatureId(7L),
                title = "复习",
                description = "章节一",
                courseReference = TodoCourseReference.External(42L),
                source = TodoFeatureSource.EXTERNAL,
                deadline = 99L,
                completed = true,
            ),
            entity.toFeatureModel(),
        )
    }

    @Test
    fun `unknown entity source falls back to local feature source`() {
        val entity = TodoEntity(
            title = "本地",
            description = "",
            courseId = null,
            source = "legacy-source",
            deadline = null,
            createdTime = 1L,
            updatedTime = 1L,
        )

        assertEquals(TodoFeatureSource.LOCAL, entity.toFeatureModel().source)
    }

    @Test
    fun `course option maps to feature selector model`() {
        val option = TodoCourseOption(42L, "高等数学", "教师", TodoSource.TRONCLASS)

        assertEquals(
            TodoCourseOptionModel(
                reference = TodoCourseReference.External(42L),
                name = "高等数学",
                teacher = "教师",
            ),
            option.toFeatureModel(),
        )
    }
}
