package com.xmu.course.contracts.todo

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoRefreshContractTest {
    @Test
    fun `reader exposes feature level success result`() = runBlocking {
        val reader = TodoRefreshReader { TodoRefreshResult.Success(importedCount = 3) }

        assertEquals(TodoRefreshResult.Success(3), reader.refresh())
    }

    @Test
    fun `reader can report session expiration without provider details`() = runBlocking {
        val reader = TodoRefreshReader { TodoRefreshResult.SessionExpired }

        assertEquals(TodoRefreshResult.SessionExpired, reader.refresh())
    }

    @Test
    fun `reader can report a generic failure`() = runBlocking {
        val reader = TodoRefreshReader { TodoRefreshResult.Error }

        assertEquals(TodoRefreshResult.Error, reader.refresh())
    }
}
