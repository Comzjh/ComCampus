package com.xmu.course.contracts.todo.command

import com.xmu.course.contracts.todo.model.TodoCourseReference
import com.xmu.course.contracts.todo.model.TodoFeatureId
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoFeatureCommandContractTest {
    @Test
    fun createCommandUsesFeatureCourseReference() {
        val reference = TodoCourseReference.External(42L)
        val command = CreateTodoFeatureCommand("作业", "章节一", 99L, reference)

        assertEquals(reference, command.courseReference)
        assertEquals("作业", command.title)
    }

    @Test
    fun editCommandUsesFeatureIdentityAndCourseReference() {
        val reference = TodoCourseReference.Local(7L)
        val command = EditTodoFeatureCommand(TodoFeatureId(3L), "复习", "", null, reference)

        assertEquals(TodoFeatureId(3L), command.id)
        assertEquals(reference, command.courseReference)
    }
}
