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

    init {
        viewModelScope.launch {
            runCatching { repository.getCourseOptions() }
                .onSuccess { courseOptions = it; refreshWithOptions() }
        }
        viewModelScope.launch {
            repository.observeTodos()
                .catch { error ->
                    _state.value = TodoState.Error(
                        message = "待办数据读取失败，请稍后重试",
                        todos = currentTodos(),
                        courseOptions = courseOptions,
                    )
                }
                .collect { todos ->
                    _state.value = if (todos.isEmpty()) {
                        TodoState.Empty(courseOptions)
                    } else {
                        TodoState.Success(todos, courseOptions)
                    }
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
            }.onFailure { showError("待办保存失败，请重试") }
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
            }.onFailure { showError("待办修改失败，请重试") }
        }
    }

    fun setCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(TodoFeatureId(id), completed) }
                .onFailure { showError("待办状态更新失败，请重试") }
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTodo(TodoFeatureId(id)) }
                .onFailure { showError("待办删除失败，请重试") }
        }
    }

    /** 用户主动从畅课刷新作业；不依赖后台任务，也不删除本地待办。 */
    fun refreshFromTronClass() {
        val reader = refreshReader ?: return
        if (refreshJob?.isActive == true) return
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            syncMutex.withLock {
                try {
                    when (val result = reader.refresh()) {
                        is TodoRefreshResult.Success -> {
                            _syncSummary.value = TodoSyncSummary(
                                importedCount = result.importedCount,
                                automatic = false,
                            )
                        }
                        TodoRefreshResult.SessionExpired -> showError("畅课登录状态已失效，请先重新登录")
                        TodoRefreshResult.Error -> showError("畅课待办刷新失败，请稍后重试")
                    }
                } catch (_: Exception) {
                    showError("畅课待办刷新失败，请稍后重试")
                } finally {
                    _isRefreshing.value = false
                }
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
            when (val result = runCatching { coordinator.refreshIfDue() }.getOrNull()) {
                is TodoAutoSyncResult.Completed -> _autoSyncMessage.value = when (result.result) {
                    is LegacyTodoRefreshResult.Success -> {
                        _syncSummary.value = TodoSyncSummary(
                            importedCount = result.result.importedCount,
                            automatic = true,
                        )
                        null
                    }
                    LegacyTodoRefreshResult.SessionExpired -> "畅课待办登录状态已失效，请重新登录"
                    LegacyTodoRefreshResult.Error -> "畅课待办自动刷新失败，已保留本地缓存"
                }
                TodoAutoSyncResult.SkippedDisabled,
                TodoAutoSyncResult.SkippedUnauthenticated,
                TodoAutoSyncResult.SkippedThrottled,
                null,
                -> Unit
            }
        }
    }

    fun refreshCourseOptions() {
        viewModelScope.launch {
            runCatching { repository.getCourseOptions() }
                .onSuccess { courseOptions = it; refreshWithOptions() }
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

    private fun showError(message: String) {
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
