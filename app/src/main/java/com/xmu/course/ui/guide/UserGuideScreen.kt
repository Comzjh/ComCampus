package com.xmu.course.ui.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * UX-11 / E1-E2：完整功能指南。
 *
 * 内容全部来自 [GuideContent]，这里只负责渲染与定位：分步教程里的「详细教程」
 * 会带上 `section` 深链接跳过来，此时自动滚到对应章节并描边高亮，
 * 返回键回到发起教程的功能页（导航由路由栈负责，不在这里处理）。
 */
private const val HEADER_ITEMS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    highlightSection: String? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(highlightSection) }

    LaunchedEffect(highlightSection) {
        selectedSection = highlightSection
        scrollToSection(listState, highlightSection)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("使用教程") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("guide_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).testTag("guide_list"),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("ComCampus", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "从课表到学业，按章节找到你需要的操作。",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            "每章开头的「分步教程」会在对应页面高亮真实控件，跟着点即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("guide_section_chips"),
                ) {
                    items(GuideContent.sections, key = { it.id }) { section ->
                        FilterChip(
                            selected = section.id == selectedSection,
                            onClick = {
                                selectedSection = section.id
                                scope.launch { scrollToSection(listState, section.id) }
                            },
                            label = { Text(section.title) },
                            modifier = Modifier.testTag("guide_section_chip"),
                        )
                    }
                }
            }
            items(GuideContent.sections, key = { "section-" + it.id }) { section ->
                GuideSectionCard(
                    section = section,
                    index = GuideContent.sections.indexOf(section) + 1,
                    highlighted = section.id == selectedSection,
                )
            }
        }
    }
}

private suspend fun scrollToSection(listState: LazyListState, sectionId: String?) {
    val index = GuideContent.sections.indexOfFirst { it.id == sectionId }
    if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEMS)
}

@Composable
private fun GuideSectionCard(
    section: GuideSection,
    index: Int,
    highlighted: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("guide_section_" + section.id),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                section.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            section.steps.forEachIndexed { stepIndex, step ->
                Text(
                    "${stepIndex + 1}. $step",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    "提示：${section.tip}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
