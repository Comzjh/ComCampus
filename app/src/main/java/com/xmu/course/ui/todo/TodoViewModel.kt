package com.xmu.course.ui.todo

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xmu.course.XmuCourseApplication
import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.todo.TodoRefreshResult
import com.xmu.course.contracts.todo.model.TodoFeatureModel
import com.xmu.course.contracts.todo.model.TodoCourseOptionModel
import com.xmu.course.contracts.todo.command.CreateTodoFeatureCommand
import com.xmu.course.contracts.todo.command.EditTodoFeatureCommand
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.model.TodoFeatureId
import com.xmu.course.data.todo.TodoAutoSyncCoordinator
import com.xmu.course.data.todo.TodoAutoSyncResult
import com.xmu.course.data.todo.TodoRefreshResult as LegacyTodoRefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TodoViewModel(
    private val repository: TodoFeatureRepository,
    private val refreshReader: TodoRefreshReader? = null,
    private val autoSyncCoordinator: TodoAutoSyncCoordinator? = null,
    private val foregroundIntervalMillis: Long = 60L * 60L * 1000L,
) : ViewModel() {
    private val _state = MutableStateFlow<TodoState>(TodoState.Loading)
    val state: StateFlow<TodoState> = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _autoSyncMessage = MutableStateFlow<String?>(null)
    val autoSyncMessage: StateFlow<String?> = _autoSyncMessage.asStateFlow()
    private val _syncSummary = MutableStateFlow<TodoSyncSummary?>(null)
    val syncSummary: StateFlow<TodoSyncSummary?> = _syncSummary.asStateFlow()
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var foregroundJob: kotlinx.coroutines.Job? = null
    private val syncMutex = Mutex()

    private var courseOptions: List<TodoCourseOptionModel> = emptyList()
    private val courseOptionsMutex = Mutex()
    private var todoReadFailed = false

    init {
        viewModelScope.launch {
            repository.observeTodos()
                .catch { error ->
                    if (error is CancellationException) throw error
                    todoReadFailed = true
                    _state.value = TodoState.Error(
                        message = "待办数据读取失败，请稍后重试",
                        todos = currentTodos(),
                        courseOptions = courseOptions,
                    )
                }
                .collect { todos ->
                    todoReadFailed = false
                    _state.value = if (todos.isEmpty()) {
                        TodoState.Empty(courseOptions)
                    } else {
                        TodoState.Success(todos, courseOptions)
                    }
                    // Other screens can import Todos directly, without this ViewModel's refresh methods.
                    reloadCourseOptions()
                }
        }
    }

    fun addTodo(
        title: String,
        description: String,
        deadline: Long?,
        course: TodoCourseOptionModel?,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addTodo(
                    CreateTodoFeatureCommand(
                        title = title,
                        description = description,
                        deadline = deadline,
                        courseReference = course?.reference,
                    ),
                )
            }.onFailure { showOperationError("待办保存失败，请重试", it) }
        }
    }

    fun updateTodo(
        todo: TodoFeatureModel,
        title: String,
        description: String,
        deadline: Long?,
        course: TodoCourseOptionModel?,
    ) {
        viewModelScope.launch {
            runCatching {
                val command = EditTodoFeatureCommand(
                    id = todo.id,
                    title = title,
                    description = description,
                    deadline = deadline,
                    courseReference = course?.reference,
                )
                repository.updateTodo(command)
            }.onFailure { showOperationError("待办修改失败，请重试", it) }
        }
    }

    fun setCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(TodoFeatureId(id), completed) }
                .onFailure { showOperationError("待办状态更新失败，请重试", it) }
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTodo(TodoFeatureId(id)) }
                .onFailure { showOperationError("待办删除失败，请重试", it) }
        }
    }

    /** 用户主动从畅课刷新作业；不依赖后台任务，也不删除本地待办。 */
    fun refreshFromTronClass() {
        val reader = refreshReader ?: return
        if (refreshJob?.isActive == true) return
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            try {
                syncMutex.withLock {
                    when (val result = reader.refresh()) {
                        is TodoRefreshResult.Success -> onSyncSuccess(result.importedCount, automatic = false)
                        TodoRefreshResult.SessionExpired -> showError("畅课登录状态已失效，请先重新登录")
                        TodoRefreshResult.Error -> showError("畅课待办刷新失败，请稍后重试")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("畅课待办刷新失败，请稍后重试")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Todo 页面进入前台时触发一次，随后每小时检查一次；离开前台即停止。 */
    fun onForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = viewModelScope.launch {
            runAutoSyncIfDue()
            while (isActive) {
                delay(foregroundIntervalMillis)
                runAutoSyncIfDue()
            }
        }
    }

    fun onBackground() {
        foregroundJob?.cancel()
        foregroundJob = null
    }

    private suspend fun runAutoSyncIfDue() {
        val coordinator = autoSyncCoordinator ?: return
        syncMutex.withLock {
            val result = try {
                coordinator.refreshIfDue()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@withLock
            }
            when (result) {
                is TodoAutoSyncResult.Completed -> when (val refreshResult = result.result) {
                    is LegacyTodoRefreshResult.Success -> onSyncSuccess(refreshResult.importedCount, automatic = true)
                    LegacyTodoRefreshResult.SessionExpired -> {
                        _autoSyncMessage.value = "畅课待办登录状态已失效，请重新登录"
                    }
                    LegacyTodoRefreshResult.Error -> {
                        _autoSyncMessage.value = "畅课待办自动刷新失败，已保留本地缓存"
                    }
                }
                TodoAutoSyncResult.SkippedDisabled,
                TodoAutoSyncResult.SkippedUnauthenticated,
                TodoAutoSyncResult.SkippedThrottled,
                -> Unit
            }
        }
    }

    private suspend fun onSyncSuccess(importedCount: Int, automatic: Boolean) {
        reloadCourseOptions()
        _syncSummary.value = TodoSyncSummary(importedCount, automatic)
        _autoSyncMessage.value = null
        // Network success cannot recover a failed local Todo subscription.
        if (!todoReadFailed) {
            _state.update { current ->
                if (current is TodoState.Error) {
                    if (current.todos.isEmpty()) TodoState.Empty(courseOptions)
                    else TodoState.Success(current.todos, courseOptions)
                } else {
                    current
                }
            }
        }
    }

    fun refreshCourseOptions() {
        viewModelScope.launch { reloadCourseOptions() }
    }

    private suspend fun reloadCourseOptions() {
        courseOptionsMutex.withLock {
            try {
                courseOptions = repository.getCourseOptions()
                refreshWithOptions()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last directory; a directory failure must not stop Todo observation.
            }
        }
    }

    private fun refreshWithOptions() {
        _state.update { current ->
            when (current) {
                is TodoState.Empty -> current.copy(courseOptions = courseOptions)
                is TodoState.Success -> current.copy(courseOptions = courseOptions)
                is TodoState.Error -> current.copy(courseOptions = courseOptions)
                TodoState.Loading -> current
            }
        }
    }

    private fun showOperationError(message: String, error: Throwable) {
        if (error is CancellationException) throw error
        showError(message)
    }

    private fun showError(message: String) {
        if (todoReadFailed) return
        _state.value = TodoState.Error(message, currentTodos(), courseOptions)
    }

    private fun currentTodos(): List<TodoFeatureModel> = when (val current = _state.value) {
        is TodoState.Success -> current.todos
        is TodoState.Error -> current.todos
        else -> emptyList()
    }
}

class TodoViewModelFactory(
    private val repository: TodoFeatureRepository,
    private val refreshReader: TodoRefreshReader? = null,
    private val autoSyncCoordinator: TodoAutoSyncCoordinator? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TodoViewModel::class.java))
        return TodoViewModel(repository, refreshReader, autoSyncCoordinator) as T
    }

    companion object {
        fun fromApplication(application: Application): TodoViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("TodoViewModel requires XmuCourseApplication")
            val todo = app.appContainer.todo
            return TodoViewModelFactory(
                repository = todo.featureRepository,
                refreshReader = todo.refreshReader,
                autoSyncCoordinator = todo.autoSyncCoordinator,
            )
        }
    }
}
