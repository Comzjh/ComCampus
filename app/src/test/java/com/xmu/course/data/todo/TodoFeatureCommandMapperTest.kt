package com.xmu.course.data.todo

import com.xmu.course.data.todo.model.TodoSource
import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoFeatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoFeatureCommandMapperTest {
    @Test
    fun `feature model maps to existing update command without persistence fields`() {
        val model = TodoFeatureModel(
            id = TodoFeatureId(7L),
            title = "新标题",
            description = "新描述",
            courseReference = TodoCourseReference.External(42L),
            source = TodoFeatureSource.EXTERNAL,
            deadline = 99L,
            completed = true,
        )

        assertEquals(
            UpdateTodoCommand(7L, "新标题", "新描述", 99L, 42L, TodoSource.TRONCLASS),
            model.toUpdateCommand(
                title = model.title,
                description = model.description,
                deadline = model.deadline,
                courseId = model.courseReference?.id,
                source = TodoSource.TRONCLASS,
            ),
        )
    }
}
