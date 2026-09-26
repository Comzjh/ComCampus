package com.xmu.course.ui.guide

/**
 * UX-11 / E1：详细帮助内容的唯一来源。
 *
 * `UserGuideScreen` 只负责渲染，教程浮层的「详细教程」跳转和设置里的入口都读这里，
 * 避免同一个功能在三个地方写出三种说法。
 *
 * 契约：每条文案都必须能在当前代码里找到对应实现（导航路由、UI 控件或行为测试）。
 * 不存在的功能一律不写，尤其不写“更多操作”这类含糊描述——长按在课程管理里
 * 就是删除，指南必须说清楚。
 */
data class GuideSection(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val tip: String,
    /** `TutorialRegistry` 里对应的分步教程 id；该页面没有分步教程时为 null。 */
    val relatedTutorialId: String?,
)

object GuideContent {
    const val SECTION_HOME = "home"
    const val SECTION_TIMETABLE = "timetable"
    const val SECTION_TODO = "todo"
    const val SECTION_ACADEMIC = "academic"
    const val SECTION_SIMULATION = "simulation"
    const val SECTION_SETTINGS = "settings"
    const val SECTION_DATA = "data_management"

    val sections: List<GuideSection> = listOf(
        GuideSection(
            id = SECTION_HOME,
            title = "首页",
            summary = "打开应用先看今天：下一节课、当天剩下的课和需要处理的待办。",
            steps = listOf(
                "首页按当前时间给出「下一节」和今天的课程，不用先进课表页。",
                "「需要处理」只列未完成且逾期、今天或临近的待办，可以直接「添加待办」。",
                "还没有课表数据时，这里会给出「导入课表」入口。",
                "底部依次是首页、课表、待办、学业和设置五个区域。",
            ),
            tip = "首页读的都是本机已保存的数据，断网也能正常查看。",
            relatedTutorialId = null,
        ),
        GuideSection(
            id = SECTION_TIMETABLE,
            title = "课表",
            summary = "周视图看安排，也能直接在本机改课。",
            steps = listOf(
                "左右滑动切换教学周；点顶部周次打开周选择器，可以直接跳到某一周。",
                "点「回到本周」复位到实际教学周，翻到别的周之后随时可用。",
                "点课程卡查看详情（教师、地点、时间、备注），也能在这里标记翘课。",
                "点课表里的空白格子，会带着那一格的星期和节次直接打开新建课程。",
                "连堂课保持连续高度，同时段的冲突课程自动分栏，不会互相盖住。",
            ),
            tip = "翘课只把课程显示变淡：周次、位置和分栏都不变，也不会再作为下一节课提醒。",
            relatedTutorialId = "timetable",
        ),
        GuideSection(
            id = SECTION_TODO,
            title = "待办",
            summary = "本地待办和畅课作业放在同一个列表里排序。",
            steps = listOf(
                "点「新增待办」手动创建：标题必填，描述和截止日期可选。",
                "点条目左侧圆圈切换完成状态，已完成的会划掉并归入「已完成」。",
                "来源标签区分「来自畅课」和「本地」，两类都可以编辑或删除。",
                "顶部按「全部 / 1 天 / 3 天 / 7 天 / 长期」筛选时间范围。",
                "课表里的课程可以和待办关联，关联后条目上会显示课程名。",
            ),
            tip = "「刷新畅课待办」由你主动点击触发，应用不会在后台自动访问。",
            relatedTutorialId = "todo",
        ),
        GuideSection(
            id = SECTION_ACADEMIC,
            title = "学业",
            summary = "培养方案、GPA、本学期课程和历史成绩，全部来自本机缓存。",
            steps = listOf(
                "「学业概览」给出已修学分、剩余学分、本学期在修门数和学分待确认门数。",
                "「培养方案」查看方案进度与学期安排；方案外课程要在详情页确认归属。",
                "「GPA 计算」是按逐课绩点加权在本机算出的估算，不是教务系统给出的 GPA。",
                "「历史成绩」按学期列出刷新后保存在本机的那份成绩。",
                "「数据与来源」里点「刷新学业数据」才会读取教务页面。",
            ),
            tip = "App 不会后台自动访问教务系统：登录信息只留在学校页面里，ComCampus 不读取、不保存你的账号凭据。",
            relatedTutorialId = "academic_home",
        ),
        GuideSection(
            id = SECTION_SIMULATION,
            title = "学业模拟",
            summary = "拖动分数试算 GPA 会怎么变，不动任何真实记录。",
            steps = listOf(
                "先确认「基线成绩」和「本学期课程」，这两块是模拟的输入。",
                "调整分数时「预计结果」里的预计 GPA 会实时更新，不用先点按钮。",
                "目标 GPA 是可选项：不设目标也能直接看预计结果。",
                "「自定义课程」用来试算还没选课的成绩影响。",
                "想换一轮假设，点「重置」回到真实数据。",
            ),
            tip = "模拟结果不会修改真实成绩，也不会写回培养方案或学业缓存。",
            relatedTutorialId = "academic_simulation",
        ),
        GuideSection(
            id = SECTION_SETTINGS,
            title = "设置",
            summary = "开学日期、课表外观、账号与帮助都在这里。",
            steps = listOf(
                "开学日期决定当前教学周怎么算，改完周次会跟着更新。",
                "课表显示设置可调整课程高度、圆角、字号、网格线和教师/地点/备注的显示。",
                "外观里可切换深色模式与字体大小。",
                "课程管理用于本机增删改课；长按课程会先要求确认，确认后才删除。",
                "「帮助与教程」随时可以重看新手指南和完整功能指南。",
            ),
            tip = "设置项都保存在本机，不需要注册任何账号。",
            relatedTutorialId = "settings",
        ),
        GuideSection(
            id = SECTION_DATA,
            title = "数据管理",
            summary = "看清哪些数据存在这台手机上，以及怎么安全地清掉。",
            steps = listOf(
                "「数据管理」列出保存在这台手机上的课表、学业和待办数据。",
                "删除前会再次确认；删除只影响本机缓存，不会改动学校系统里的记录。",
                "提示「本机缓存异常」时，可以先清除再重新刷新学业数据。",
                "「账号与授权」管理畅课的登录状态与校园服务开关。",
            ),
            tip = "应用已关闭系统备份和设备间迁移，课表、学业和登录数据不会随备份离开这台手机。",
            relatedTutorialId = "data_management",
        ),
    )

    fun find(id: String?): GuideSection? = sections.firstOrNull { it.id == id }

    /** E2：分步教程结束后要跳去哪一章详细指南。 */
    fun sectionIdForTutorial(tutorialId: String): String?
        = sections.firstOrNull { it.relatedTutorialId == tutorialId }?.id
}
