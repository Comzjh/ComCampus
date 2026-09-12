package com.xmu.course.ui.background

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class BackgroundUiState(
    val timetableName: String? = null,
    val config: TimetableConfig? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TimetableRepository(AppDatabase.getInstance(application))
    private val _state = MutableStateFlow(BackgroundUiState())
    val state: StateFlow<BackgroundUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            TimetablePrefs.currentTimetableId.flatMapLatest { id ->
                if (id == null) flowOf(BackgroundUiState())
                else combine(repo.observeTimetable(id), repo.observeConfig(id)) { tt, config ->
                    BackgroundUiState(tt?.name, config)
                }
            }.collect { _state.value = it }
        }
    }

    fun update(transform: (TimetableConfig) -> TimetableConfig) {
        val config = _state.value.config ?: return
        viewModelScope.launch { repo.updateBackground(transform(config)) }
    }

    fun selectNone() = update { it.copy(backgroundType = BackgroundType.NONE, backgroundValue = null) }

    fun selectSolid(value: String) = update {
        it.copy(
            backgroundType = BackgroundType.SOLID,
            backgroundValue = value,
            overlayAlpha = 0f,
        )
    }

    fun selectBuiltIn(value: String) = update {
        it.copy(
            backgroundType = BackgroundType.BUILT_IN,
            backgroundValue = value,
            blurRadius = BackgroundPipelineDefaults.BUILT_IN_BLUR_RADIUS_DP,
            overlayColor = BackgroundPipelineDefaults.BUILT_IN_OVERLAY_COLOR,
            overlayAlpha = BackgroundPipelineDefaults.BUILT_IN_OVERLAY_ALPHA,
        )
    }

    fun useCustomImage(uri: String) = update {
        it.copy(
            backgroundType = BackgroundType.CUSTOM,
            backgroundValue = uri,
            blurRadius = BackgroundPipelineDefaults.BUILT_IN_BLUR_RADIUS_DP,
            overlayColor = BackgroundPipelineDefaults.BUILT_IN_OVERLAY_COLOR,
            overlayAlpha = BackgroundPipelineDefaults.BUILT_IN_OVERLAY_ALPHA,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerScreen(
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackgroundPickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.useCustomImage(uri.toString())
            onOpenEditor()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表背景") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val config = state.config
            if (config == null) {
                Text("还没有当前课表，请先在课表管理中新建或切换。")
                return@Column
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前预览", style = MaterialTheme.typography.titleMedium)
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        BackgroundContent(config)
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("默认", style = MaterialTheme.typography.titleMedium)
                    val noneSelected = config.backgroundType == BackgroundType.NONE
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Text("不使用背景", Modifier.weight(1f).padding(start = 12.dp))
                        FilterChip(
                            selected = noneSelected,
                            onClick = viewModel::selectNone,
                            label = { Text(if (noneSelected) "使用中" else "使用") },
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("厦大主题", style = MaterialTheme.typography.titleMedium)
                    val labels = mapOf(
                        "jiageng" to "嘉庚楼群",
                        "furong_lake" to "芙蓉湖",
                        "shangxian" to "上弦场",
                        "siming_night" to "思明校区夜景",
                        "xiangan" to "翔安校区",
                    )
                    xmuBackgrounds.forEach { (value, resId) ->
                        val selected = config.backgroundType == BackgroundType.BUILT_IN &&
                            config.backgroundValue == value
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(resId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(labels[value] ?: value)
                                Text(
                                    "内置主题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectBuiltIn(value) },
                                label = { Text(if (selected) "使用中" else "使用") },
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("纯色背景", style = MaterialTheme.typography.titleMedium)
                    solidBackgrounds.forEach { (label, colorValue) ->
                        val selected = config.backgroundType == BackgroundType.SOLID &&
                            config.backgroundValue == colorValue
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(solidColor(colorValue)),
                            )
                            Text(label, Modifier.weight(1f).padding(start = 12.dp))
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectSolid(colorValue) },
                                label = { Text(if (selected) "使用中" else "使用") },
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    var blur by remember(config.blurRadius) { mutableFloatStateOf(config.blurRadius.toFloat()) }
                    Text("背景模糊度 ${blur.toInt()}dp")
                    Slider(
                        value = blur,
                        onValueChange = { value -> blur = value },
                        onValueChangeFinished = {
                            viewModel.update { it.copy(blurRadius = blur.toInt().coerceIn(0, 25)) }
                        },
                        valueRange = 0f..25f,
                        enabled = config.backgroundType != BackgroundType.SOLID,
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("我的图片", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅保存本地图片 URI，不上传，不复制到云端。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("选择图片") }
                    if (config.backgroundType == BackgroundType.CUSTOM) {
                        OutlinedButton(
                            onClick = onOpenEditor,
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("编辑裁剪 / 遮罩") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackgroundPickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val config = state.config
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑背景") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (config == null) {
            Text("没有可编辑背景", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            var scale by remember(config.cropScale) { mutableFloatStateOf(config.cropScale) }
            var offsetX by remember(config.cropOffsetX) { mutableStateOf(config.cropOffsetX) }
            var offsetY by remember(config.cropOffsetY) { mutableStateOf(config.cropOffsetY) }
            val density = LocalDensity.current

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = with(density) { 120.dp.toPx() }
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxX, maxX)
                            scale = newScale
                            viewModel.update { current ->
                                current.copy(
                                    cropScale = scale,
                                    cropOffsetX = offsetX,
                                    cropOffsetY = offsetY,
                                )
                            }
                        }
                    },
            ) {
                BackgroundContent(config.copy(cropScale = scale, cropOffsetX = offsetX, cropOffsetY = offsetY))
            }

            Text("缩放", Modifier.padding(top = 16.dp))
            Slider(
                value = scale,
                onValueChange = { value ->
                    scale = value
                    viewModel.update { it.copy(cropScale = value) }
                },
                valueRange = 1f..4f,
            )

            Text("模糊 ${config.blurRadius}dp")
            Slider(
                value = config.blurRadius.toFloat(),
                onValueChange = { value -> viewModel.update { it.copy(blurRadius = value.toInt().coerceIn(0, 25)) } },
                valueRange = 0f..25f,
            )

            Text("遮罩透明度 ${(config.overlayAlpha * 100).toInt()}%")
            Slider(
                value = config.overlayAlpha,
                onValueChange = { value -> viewModel.update { it.copy(overlayAlpha = value.coerceIn(0f, 0.70f)) } },
                valueRange = 0f..0.70f,
            )

            Text("遮罩颜色")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(
                    "自动" to 0,
                    "黑色" to 0xFF000000.toInt(),
                    "白色" to 0xFFFFFFFF.toInt(),
                    "厦大蓝" to 0xFF1A5799.toInt(),
                )
                colors.forEach { (label, colorValue) ->
                    FilterChip(
                        selected = config.overlayColor == colorValue,
                        onClick = { viewModel.update { it.copy(overlayColor = colorValue) } },
                        label = { Text(label) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.update { it.copy(cropOffsetX = 0f, cropOffsetY = 0f, cropScale = 1f) } }) {
                    Text("居中")
                }
                OutlinedButton(onClick = onBack) { Text("完成") }
            }
        }
    }
}
