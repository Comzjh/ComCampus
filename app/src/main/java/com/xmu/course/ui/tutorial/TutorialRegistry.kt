package com.xmu.course.ui.tutorial

/**
 * Page tutorials. Copy follows the milestone style rules:
 * short, direct, second-person guidance; no "该组件用于..." phrasing.
 */
object TutorialRegistry {
    private val definitions = listOf(
        TutorialDefinition(
            id = "academic_home",
            steps = listOf(
                // BUG-09 / D4：首步不再锚定「学业概览」——那张卡片只在已有数据时存在，
                // 新用户首次打开教程必然找不到目标。引导步没有目标才是正确形态。
                TutorialStep(
                    null,
                    "学业",
                    "这里可以查看培养方案、GPA、本学期课程和历史成绩。",
                ),
                TutorialStep(
                    TutorialTargetKey.ACADEMIC_SIMULATION_ENTRY,
                    "学业模拟",
                    "点这里试算成绩影响，模拟不会修改真实成绩。",
                ),
                TutorialStep(
                    TutorialTargetKey.ACADEMIC_DATA_SOURCES,
                    "数据与来源",
                    "刷新和数据管理都收在这里，数据只保存在本机。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "academic_simulation",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.SIMULATION_BASELINE,
                    "当前基线",
                    "先核对已修学分和绩点，模拟从这里开始。",
                ),
                TutorialStep(
                    TutorialTargetKey.SIMULATION_SEMESTER,
                    "在修课程",
                    "选择课程成绩，看看 GPA 会怎么变化。",
                ),
                TutorialStep(
                    TutorialTargetKey.SIMULATION_RESULT,
                    "预计结果",
                    "填好分数就在这里更新，不设目标也能看预计 GPA。",
                ),
                TutorialStep(
                    TutorialTargetKey.SIMULATION_TARGET_GPA,
                    "目标绩点（可选）",
                    "想看出差距，再设一个目标 GPA。",
                ),
                TutorialStep(
                    TutorialTargetKey.SIMULATION_CUSTOM,
                    "自定义模拟",
                    "想提前试算一门课，加在这里。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "academic_plan",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.PLAN_OVERVIEW,
                    "培养方案进度",
                    "这里显示整体修读进度。",
                ),
                TutorialStep(
                    TutorialTargetKey.PLAN_SEMESTER,
                    "本学期",
                    "点这里查看本学期在修课程，待确认的学分也在这里补。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "academic_data_sources",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.SOURCES_REFRESH,
                    "刷新学业数据",
                    "这里可以重新读取教务数据，不会修改学校系统中的内容。",
                ),
                TutorialStep(
                    TutorialTargetKey.SOURCES_LOCAL,
                    "本机数据",
                    "查看和管理本机数据，删除只影响本机缓存。",
                ),
                TutorialStep(
                    TutorialTargetKey.SOURCES_ORIGINAL,
                    "原始页面",
                    "需要看学校页面时，从这里跳转。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "timetable",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.TIMETABLE_WEEK_SWITCH,
                    "切换周次",
                    "点这里切换查看不同周课表。",
                ),
                TutorialStep(
                    TutorialTargetKey.TIMETABLE_COURSE_CARD,
                    "课程详情",
                    "点课程可以看详情。",
                ),
                TutorialStep(
                    TutorialTargetKey.TIMETABLE_SETTINGS,
                    "课表设置",
                    "点这里调整课表样式。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "todo",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.TODO_LIST,
                    "待办列表",
                    "你的待办都列在这里。",
                ),
                TutorialStep(
                    TutorialTargetKey.TODO_CHECKBOX,
                    "完成待办",
                    "点圆圈即可完成或恢复待办。",
                ),
                TutorialStep(
                    TutorialTargetKey.TODO_ADD,
                    "新建待办",
                    "点这里新建一条待办。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "settings",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.SETTINGS_ACCOUNT,
                    "账户",
                    "打开个人中心，查看登录状态、数据来源和支持入口。",
                ),
                TutorialStep(
                    TutorialTargetKey.SETTINGS_APPEARANCE,
                    "外观",
                    "应用深浅色跟随系统；这里可开启 Android 12+ 的壁纸取色。",
                ),
                TutorialStep(
                    TutorialTargetKey.SETTINGS_TIMETABLE,
                    "课表显示",
                    "调整当前课表的背景、网格、信息显隐和文字排版。",
                ),
                TutorialStep(
                    TutorialTargetKey.SETTINGS_UPDATE,
                    "应用更新",
                    "发现新版本时会标出底部设置；进入设置后才显示更新提示。",
                ),
                TutorialStep(
                    TutorialTargetKey.SETTINGS_ABOUT,
                    "关于",
                    "在这里查看 ComCampus 版本和开源协议。",
                ),
            ),
        ),
        TutorialDefinition(
            id = "data_management",
            steps = listOf(
                TutorialStep(
                    TutorialTargetKey.DATA_LIST,
                    "本机数据",
                    "各类本机数据按来源列在这里。",
                ),
                TutorialStep(
                    TutorialTargetKey.DATA_DELETE,
                    "删除数据",
                    "删除只清除本机缓存，不影响学校系统。",
                ),
            ),
        ),
    )

    private val byId = definitions.associateBy { it.id }

    fun get(id: String): TutorialDefinition? = byId[id]

    fun ids(): List<String> = definitions.map { it.id }
}
