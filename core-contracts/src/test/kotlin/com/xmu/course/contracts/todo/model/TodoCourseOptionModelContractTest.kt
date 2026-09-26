package com.xmu.course.contracts.todo.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TodoCourseOptionModelContractTest {
    @Test
    fun modelUsesSourceQualifiedReference() {
        val option = TodoCourseOptionModel(
            reference = TodoCourseReference.Local(7L),
            name = "高等数学",
            teacher = "教师",
        )

        assertEquals(TodoFeatureSource.LOCAL, option.reference.featureSource)
    }
}
