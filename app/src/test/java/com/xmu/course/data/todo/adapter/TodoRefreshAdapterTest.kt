package com.xmu.course.data.todo.adapter

import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoRefreshAdapterTest {
    @Test
    fun mapsSuccessfulRefreshWithoutExposingLegacyResult() = runTest {
        val adapter = TodoRefreshAdapter(
            TodoRefreshCoordinator { LegacyTodoRefreshResult.Success(importedCount = 5) },
        )

        assertEquals(TodoRefreshResult.Success(5), adapter.refresh())
    }

    @Test
    fun mapsSessionExpirationToFeatureResult() = runTest {
        val adapter = TodoRefreshAdapter {
            LegacyTodoRefreshResult.SessionExpired
        }

        assertEquals(TodoRefreshResult.SessionExpired, adapter.refresh())
    }

    @Test
    fun mapsFailureToGenericFeatureResult() = runTest {
        val adapter = TodoRefreshAdapter {
            LegacyTodoRefreshResult.Error
        }

        assertEquals(TodoRefreshResult.Error, adapter.refresh())
    }
}
