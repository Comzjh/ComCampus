package com.xmu.course.data.todo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.todo.model.TodoEntity
import com.xmu.course.data.todo.model.TodoSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Interleave real Room reads/writes at the existing, nontransactional edit boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoCompletionInterleavingAuditTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun completingTodoWhileEditIsSavingDoesNotBecomeIncompleteAgain() = verifyInterleaving(
        initiallyCompleted = false,
        latestCompleted = true,
    )

    @Test
    fun restoringTodoWhileEditIsSavingDoesNotBecomeCompletedAgain() = verifyInterleaving(
        initiallyCompleted = true,
        latestCompleted = false,
    )

    private fun verifyInterleaving(initiallyCompleted: Boolean, latestCompleted: Boolean) = runTest {
        val dao = db.todoDao()
        val id = dao.insert(
            TodoEntity(
                title = "Synthetic old title",
                description = "",
                courseId = null,
                deadline = null,
                completed = initiallyCompleted,
                createdTime = 1L,
                updatedTime = 1L,
            ),
        )
        val editHasRead = CompletableDeferred<Unit>()
        val allowEditToContinue = CompletableDeferred<Unit>()
        val gatedDao = object : TodoDao by dao {
            override suspend fun getById(id: Long): TodoEntity? {
                val snapshot = dao.getById(id)
                editHasRead.complete(Unit)
                allowEditToContinue.await()
                return snapshot
            }
        }
        val repository = TodoRepository(gatedDao, clock = { 2L })
        val edit = async {
            repository.updateTodo(
                UpdateTodoCommand(
                    id = id,
                    title = "Synthetic edited title",
                    description = "",
                    deadline = null,
                    courseId = null,
                    source = TodoSource.LOCAL,
                ),
            )
        }

        editHasRead.await()
        // The editor has dismissed; the user can now toggle the same row's checkbox.
        repository.setCompleted(id, latestCompleted)
        val completionAfterToggle = dao.getById(id)!!.completed
        allowEditToContinue.complete(Unit)
        edit.await()

        assertEquals("The checkbox change is persisted before the edit resumes", latestCompleted, completionAfterToggle)
        val saved = dao.getById(id)!!
        assertEquals("The title edit itself is saved", "Synthetic edited title", saved.title)
        assertEquals("Saving a title must preserve the user's newer completion state", latestCompleted, saved.completed)
    }
}
