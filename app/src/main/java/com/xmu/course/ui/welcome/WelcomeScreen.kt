package com.xmu.course.ui.welcome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * UX-12 / E4-E5：首次启动引导。
 *
 * 横向 pager，可滑动也可用按钮；「跳过」和最后一页的「完成」都只调用同一个
 * [onGetStarted]，不要求看完，也不要求看完才能用应用。
 *
 * 每一页只讲一个页面 + 2–4 条本机代码里确实存在的操作；不写没实现的功能。
 * 只在此处展示一次（SharedPreferences 标记），教程入口本身常驻页面左侧。
 */
internal data class WelcomePage(
    val title: String,
    val body: String,
    val tips: List<String>,
)

internal val WELCOME_PAGES: List<WelcomePage> = listOf(
    WelcomePage(
        title = "欢迎使用 ComCampus",
        body = "课表、待办和学业信息都保存在本机，不上传、不需要账号。",
        tips = listOf(
            "离网也能看课表和待办。",
            "教务登录只留在学校页面里，应用不读取你的凭据。",
        ),
    ),
    WelcomePage(
        title = "首页",
        body = "打开应用先看今天有什么。",
        tips = listOf(
            "按当前时间给出下一节课和今天剩下的课程。",
            "「需要处理」列出逾期、今天和临近的待办。",
            "还没有课表时，这里直接给导入入口。",
        ),
    ),
    WelcomePage(
        title = "课表",
        body = "周视图安排每一天，也能在本机改课。",
        tips = listOf(
            "左右滑动切换教学周，点顶部周次打开周选择器。",
            "点空白格子就能在那一格新建课程。",
            "点课程卡看详情、改颜色，或标记翘课。",
            "翻到别的周之后，点「回到本周」复位。",
        ),
    ),
    WelcomePage(
        title = "待办",
        body = "手写的待办和畅课作业排在同一个列表里。",
        tips = listOf(
            "点圆圈标记完成，已完成的会划掉并归入「已完成」。",
            "条目上的来源标签区分「来自畅课」和「本地」。",
            "按 1 天 / 3 天 / 7 天 / 长期筛选时间范围。",
        ),
    ),
    WelcomePage(
        title = "学业",
        body = "培养方案、GPA、本学期和历史成绩。",
        tips = listOf(
            "点「刷新学业数据」才会读取教务页面。",
            "本地 GPA 是按逐课绩点加权算出的估算。",
            "方案外课程要先确认归属，才会给出结论。",
        ),
    ),
    WelcomePage(
        title = "学业模拟",
        body = "拖动分数，看看 GPA 会怎么变。",
        tips = listOf(
            "调整分数时预计 GPA 实时更新，不用先点按钮。",
            "目标 GPA 是可选项，不设也能看预计结果。",
            "模拟不会修改真实成绩。",
        ),
    ),
    WelcomePage(
        title = "隐藏功能与教程",
        body = "几个不点出来你可能不会发现的操作。",
        tips = listOf(
            "翘课：课程保留但显示变淡，也不再作为下一节课提醒。",
            "长按课程管理里的课程会删除它，删除前会先要求确认。",
            "页面顶部工具栏的帮助按钮会在当前页高亮真实控件。",
            "设置 → 帮助与教程 随时可以重看新手指南和完整指南。",
        ),
    ),
)

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { WELCOME_PAGES.size })
    val scope = rememberCoroutineScope()
    val lastIndex = WELCOME_PAGES.lastIndex
    val isLastPage = pagerState.currentPage == lastIndex

    Surface(
        modifier = modifier.fillMaxSize().testTag("welcome_surface"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("welcome_pager"),
            ) { page ->
                WelcomePageContent(WELCOME_PAGES[page])
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${WELCOME_PAGES.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("welcome_page_indicator"),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (isLastPage || pagerState.currentPage == 0) {
                        onGetStarted()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(when {
                        pagerState.currentPage == 0 -> "welcome_start"
                        isLastPage -> "welcome_finish"
                        else -> "welcome_next"
                    }),
            ) {
                Text(when {
                    pagerState.currentPage == 0 -> "开始使用"
                    isLastPage -> "继续设置启动页"
                    else -> "下一页"
                })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier.testTag("welcome_previous"),
                    ) { Text("上一页") }
                } else {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        modifier = Modifier.testTag("welcome_learn_more"),
                    ) { Text("了解功能") }
                }
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = onGetStarted,
                        modifier = Modifier.testTag("welcome_skip"),
                    ) { Text("跳过引导") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WelcomePageContent(page: WelcomePage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        page.tips.forEach { tip ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    tip,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
