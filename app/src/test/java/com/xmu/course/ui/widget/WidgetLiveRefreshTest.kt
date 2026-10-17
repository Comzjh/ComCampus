package com.xmu.course.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.SideEffect
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.widget.WidgetDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetLiveRefreshTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun anActiveTodoCompositionReloadsChangedDeadlineAndCompletion() {
        var edited = WidgetTodoData(0, emptyList())
        var completed = WidgetTodoData(0, emptyList())
        runGlanceAppWidgetUnitTest {
        var now = 1_000L
        var records = listOf(WidgetTodoRecord(1, "synthetic homework", null, WidgetTodoSource.TRONCLASS, 120_000L, false, 1))
        val signal = Signal()
        fun snapshot() = WidgetRepository.selectTodosForWidget(records, now, zoneId = ZoneOffset.UTC)
        val initial = snapshot()
        val updates = widgetDataUpdates(signal) { snapshot() }
        var observed = initial
        setContext(context)
        provideComposable { LiveWidgetData(initial, updates) { SideEffect { observed = it }; TodoWidgetContent(it) } }
        onNode(hasTextEqualTo("synthetic homework")).assertExists()
        onNode(hasText("已逾期")).assertDoesNotExist()
        onNode(hasTextEqualTo("↻ 刷新")).assertHasClickAction()

        records = records.map { it.copy(title = "updated homework", deadline = 500L) }
        signal.tick()
        awaitIdle()
        assertEquals("The active composition must receive the changed record", "updated homework", observed.items.single().title)
        assertTrue(observed.items.single().isOverdue)
        assertTrue(observed.items.single().countdownText!!.contains("已逾期"))
        edited = observed

        records = records.map { it.copy(completed = true) }
        signal.tick()
        awaitIdle()
        assertEquals(0, observed.totalUnfinished)
        assertTrue(observed.items.isEmpty())
        completed = observed
        }
        // Glance 1.1.1's isolated test environment caches an Idle tree while an active
        // composition's SideEffect sees new values. Check those live values above, then
        // inspect each emitted snapshot's rendered content in its own assertion tree.
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { TodoWidgetContent(edited) }
            onNode(hasTextEqualTo("synthetic homework")).assertDoesNotExist()
            onNode(hasTextEqualTo("updated homework")).assertExists()
            onNode(hasText("已逾期")).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { TodoWidgetContent(completed) }
            onNode(hasTextEqualTo("待办 · 0")).assertExists()
            onNode(hasTextEqualTo("暂无待办")).assertExists()
            onNode(hasTextEqualTo("updated homework")).assertDoesNotExist()
        }
    }

    @Test
    fun anActiveNextCourseCompositionRecalculatesAtClockBoundaries() {
        val course = WidgetCourse(1, "synthetic class", "room", 1, "09:00", 540, 600, false)
        var ongoingTitle = ""
        var finishedTitle = ""
        runGlanceAppWidgetUnitTest {
        var minute = 539
        val signal = Signal()
        val updates = widgetDataUpdates(signal) { minute }
        var observed = 539
        setContext(context)
        provideComposable {
            LiveWidgetData(539, updates) { current ->
                SideEffect { observed = current }
                NextCourseContent(WidgetRepository.findNextCourse(listOf(course), current), current)
            }
        }
        onNode(hasTextEqualTo("下一节课")).assertExists()
        minute = 540
        signal.tick()
        awaitIdle()
        assertEquals("The active composition must receive the clock change", 540, observed)
        ongoingTitle = nextCourseTitle(WidgetRepository.findNextCourse(listOf(course), observed), observed)
        assertEquals("正在上课", ongoingTitle)
        minute = 600
        // Clock broadcasts have no Room write: the explicit refresh must also wake this session.
        runBlocking { WidgetUpdater.updateAll(context) }
        awaitIdle()
        assertEquals(600, observed)
        finishedTitle = nextCourseTitle(WidgetRepository.findNextCourse(listOf(course), observed), observed)
        assertEquals("今日无课程", finishedTitle)
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { NextCourseContent(course, 540) }
            onNode(hasTextEqualTo("下一节课")).assertDoesNotExist()
            // Both the heading and the countdown intentionally say that class is in progress.
            onAllNodes(hasTextEqualTo(ongoingTitle)).assertCountEquals(2)
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { NextCourseContent(null, 600) }
            onAllNodes(hasTextEqualTo(finishedTitle)).assertCountEquals(2)
        }
    }

    @Test
    fun explicitConfigurationRefreshRecomposesEvenWhenDataIsEqual() {
        val qualifiers = RuntimeEnvironment.getQualifiers()
        try {
        runGlanceAppWidgetUnitTest {
        var rendered = 0
        val updates = widgetDataUpdates(Signal()) { "unchanged" }
        setContext(context)
        provideComposable { LiveWidgetData("unchanged", updates) { SideEffect { rendered++ } } }
        awaitIdle()
        val before = rendered
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        RuntimeEnvironment.setQualifiers(if (night == Configuration.UI_MODE_NIGHT_YES) "+notnight" else "+night")
        runBlocking { WidgetUpdater.updateAll(context) }
        awaitIdle()
        assertTrue("A configuration-only refresh must re-read current theme resources", rendered > before)
        }
        } finally {
            RuntimeEnvironment.setQualifiers(qualifiers)
        }
    }

    private class Signal : WidgetDataSource {
        private val generation = MutableStateFlow(0)
        override fun observeInvalidation(): Flow<Unit> = generation.map { Unit }
        fun tick() { generation.value += 1 }
    }
}
