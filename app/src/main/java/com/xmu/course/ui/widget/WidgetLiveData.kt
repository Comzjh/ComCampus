package com.xmu.course.ui.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.LocalContext
import com.xmu.course.data.widget.WidgetDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge

/** Keep an active Glance composition connected to data and explicit clock/config refreshes. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> widgetDataUpdates(source: WidgetDataSource, reload: suspend () -> T): Flow<T> =
    merge(source.observeInvalidation(), WidgetUpdater.observeRefreshRequests()).mapLatest { reload() }

@Composable
internal fun <T> LiveWidgetData(initial: T, updates: Flow<T>, content: @Composable (T) -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf(initial) }
    var configuration by remember { mutableStateOf(Configuration(context.resources.configuration)) }
    LaunchedEffect(updates, context) {
        updates.collect {
            data = it
            configuration = Configuration(context.resources.configuration)
        }
    }
    // Theme-only changes must also invalidate children whose data parameters are unchanged.
    // Retain their identity for normal Room/clock updates so widget scrolling stays stable.
    key(configuration) { content(data) }
}
