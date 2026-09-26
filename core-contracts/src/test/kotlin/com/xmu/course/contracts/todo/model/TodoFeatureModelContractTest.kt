package com.xmu.course.contracts.todo.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TodoFeatureModelContractTest {
    @Test
    fun modelUsesFeatureSourceVocabulary() {
        val model = TodoFeatureModel(
            id = TodoFeatureId(1L),
            title = "作业",
            description = "",
            courseReference = TodoCourseReference.External(7L),
            source = TodoFeatureSource.EXTERNAL,
            deadline = null,
            completed = false,
        )

        assertEquals(TodoFeatureSource.EXTERNAL, model.source)
    }
}
