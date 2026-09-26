package com.xmu.course.di

import android.content.Context
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.TimetableRepository
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.auth.SharedPreferencesWiseduSessionMarker
import com.xmu.course.data.academicimport.PdfBoxTextExtractor
import com.xmu.course.data.import.ImportCoordinator
import com.xmu.course.data.import.provider.CourseImportProviderAdapter
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.academicimport.adapter.AcademicImportSandboxMapper
import com.xmu.course.data.academicimport.parser.AcademicReportParser
import com.xmu.course.data.academicimport.review.AcademicImportDecisionMapper
import com.xmu.course.data.academicimport.xlsx.TemporaryAcademicXlsxStore
import com.xmu.course.data.academicrecord.AcademicRecordRepository
import com.xmu.course.data.academiccompletion.AcademicCompletionDataOwner
import com.xmu.course.data.academiccompletion.AcademicCompletionStore
import com.xmu.course.data.jwgrades.JwGradeDataOwner
import com.xmu.course.data.jwgrades.JwGradeStore
import com.xmu.course.data.academicrecord.AcademicRecordImportSaver
import com.xmu.course.data.academicrecord.AcademicRecordSandboxProvider
import com.xmu.course.data.update.GitHubUpdateRepositoryContract
import com.xmu.course.data.update.GitHubUpdateRepositoryFactory
import com.xmu.course.data.update.SharedPreferencesUpdateSettings
import com.xmu.course.data.update.UpdateSettings
import com.xmu.course.data.tronclass.api.SessionCookieAuthenticator
import com.xmu.course.data.tronclass.api.SessionProvider
import com.xmu.course.data.tronclass.api.StoreSessionProvider
import com.xmu.course.data.tronclass.api.TronClassApiClient
import com.xmu.course.data.tronclass.assignment.AssignmentRepository
import com.xmu.course.data.tronclass.assignment.AssignmentRepositoryContract
import com.xmu.course.data.tronclass.assignment.AssignmentSyncSettings
import com.xmu.course.data.tronclass.assignment.SharedPreferencesAssignmentSyncSettings
import com.xmu.course.data.tronclass.adapter.TronClassFeatureRepositoryAdapter
import com.xmu.course.data.tronclass.auth.EncryptedTronSessionStore
import com.xmu.course.data.tronclass.auth.TronSessionStore
import com.xmu.course.data.tronclass.feature.TronClassFeatureRepository
import com.xmu.course.data.tronclass.repository.TronClassRepository
import com.xmu.course.data.tronclass.repository.TronClassRepositoryContract
import com.xmu.course.data.tronclass.repository.TronCourseCacheRepository
import com.xmu.course.data.tronclass.todo.OfficialTodoRepository
import com.xmu.course.data.tronclass.todo.OfficialTodoRepositoryContract
import com.xmu.course.data.todo.SharedPreferencesTodoAutoSyncSettings
import com.xmu.course.data.todo.TodoAutoSyncSettings
import com.xmu.course.data.todo.TodoAutoSyncCoordinator
import com.xmu.course.data.todo.TodoRepository
import com.xmu.course.data.todo.TodoRepositoryContract
import com.xmu.course.data.todo.TodoRefreshCoordinator
import com.xmu.course.data.todo.adapter.TodoFeatureRepositoryAdapter
import com.xmu.course.data.todo.adapter.TodoRefreshAdapter
import com.xmu.course.contracts.todo.TodoFeatureRepository
import com.xmu.course.contracts.todo.TodoRefreshReader
import com.xmu.course.contracts.presentation.OnboardingPreference
import com.xmu.course.contracts.presentation.StartupDestinationPreference
import com.xmu.course.contracts.timetable.TimetableFeatureRepository
import com.xmu.course.contracts.timetable.ViewWeekPreference
import com.xmu.course.data.presentation.SharedPreferencesOnboardingPreference
import com.xmu.course.data.presentation.SharedPreferencesStartupDestinationPreference
import com.xmu.course.data.timetable.adapter.TimetableFeatureRepositoryAdapter
import com.xmu.course.data.timetable.preferences.SharedPreferencesViewWeekPreference
import com.xmu.course.data.tronclass.repository.TronClassTodoRefreshCoordinator
import com.xmu.course.data.transcript.AcademicRecordTranscriptReader
import com.xmu.course.data.transcript.CompositeTranscriptReader
import com.xmu.course.data.transcript.manual.ManualTranscriptReader
import com.xmu.course.data.transcript.manual.ManualTranscriptRepository
import com.xmu.course.contracts.grades.TranscriptReader
import com.xmu.course.ui.tutorial.SharedPreferencesTutorialPreferenceStore
import com.xmu.course.ui.tutorial.TutorialPreferenceStore

/** TronClass 认证、API 与仓储的共享对象图。 */
class TronClassDependencies(
    val sessionStore: TronSessionStore,
    val sessionProvider: SessionProvider,
    val assignmentRepository: AssignmentRepositoryContract,
    val officialTodoRepository: OfficialTodoRepositoryContract,
    val repository: TronClassRepositoryContract,
    val featureRepository: TronClassFeatureRepository,
)

/** 更新服务的共享依赖图。 */
class UpdateDependencies(
    val repository: GitHubUpdateRepositoryContract,
    val settings: UpdateSettings,
)

/** Todo 本地仓储与畅课同步的共享依赖图。 */
class TodoDependencies(
    val repository: TodoRepositoryContract,
    val refreshCoordinator: TodoRefreshCoordinator,
    val autoSyncCoordinator: TodoAutoSyncCoordinator,
    val featureRepository: TodoFeatureRepository = TodoFeatureRepositoryAdapter(repository),
    val refreshReader: TodoRefreshReader = TodoRefreshAdapter(refreshCoordinator),
)

/** 设置页使用的本地设置能力图；具体 SharedPreferences 实现只在此组装。 */
class SettingsDependencies(
    val repository: com.xmu.course.data.SettingsDataSource,
    val assignmentSyncSettings: AssignmentSyncSettings,
    val todoAutoSyncSettings: TodoAutoSyncSettings,
)

/**
 * Grades（学业模拟）能力图。
 *
 * 模拟基线只消费本地教务缓存（培养方案 + 成绩快照 + 本地 GPA 策略）；
 * 旧的 Excel 课程来源已退出模拟流程，Legacy 导入通道只保留在 data 层与「数据管理」里。
 */
class GradesDependencies(
    val transcriptReader: TranscriptReader,
    val academicRecordSandboxProvider: AcademicRecordSandboxProvider,
    val academicCompletionStore: AcademicCompletionStore,
    val jwGradeStore: JwGradeStore,
    val academicGpaPolicyStore: com.xmu.course.data.academic.AcademicGpaPolicyStore,
)

/**
 * 应用级依赖组装入口。
 *
 * 当前只负责复用已有对象，不承载业务逻辑，也不引入 DI 框架。
 * 所有重量级对象保持 lazy，避免认证独立进程启动时提前打开主进程数据库。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val tutorialPreferenceStore: TutorialPreferenceStore by lazy {
        SharedPreferencesTutorialPreferenceStore(appContext)
    }

    /** AcademicRecord 的本地存储能力；Entity/DAO 只在 data 层组装。 */
    val academicRecordRepository: AcademicRecordRepository by lazy {
        AcademicRecordRepository(database.academicRecordDao())
    }

    /** Grades 可消费的学业记录能力；不向 Grades 暴露 Room 细节。 */
    val academicRecordSandboxProvider: AcademicRecordSandboxProvider by lazy {
        AcademicRecordSandboxProvider(academicRecordRepository)
    }


    /** 手动成绩录入的本地存储（SharedPreferences，不进 Room）。 */
    val manualTranscriptRepository: ManualTranscriptRepository by lazy {
        ManualTranscriptRepository(appContext)
    }

    /**
     * 学业完成快照（Phase 10.1）：应用私有 JSON 文件，不进 Room。
     * 只存脱敏内部快照 + 本地学分覆盖；来源原始文件（含姓名/学号）不落盘。
     */
    val academicCompletionStore: AcademicCompletionStore by lazy {
        AcademicCompletionStore(appContext.filesDir.resolve("academic_completion/snapshot.json"))
    }

    /** 学业快照的隐私数据属主；来源是本地文件导入，不属于任何 Provider。 */
    val academicCompletionDataOwner: AcademicCompletionDataOwner by lazy {
        AcademicCompletionDataOwner(academicCompletionStore)
    }

    /**
     * cjcx 官方成绩单缓存（JW 集成里程碑）：应用私有 JSON 文件，不进 Room。
     * 只存白名单业务字段与来源元数据；不含身份、凭据、URL。
     */
    val jwGradeStore: JwGradeStore by lazy {
        JwGradeStore(appContext.filesDir.resolve("jw_grades/cjcx_grades.json"))
    }

    /** 成绩单缓存的隐私数据属主；数据来自用户手动刷新的官方只读查询。 */
    val jwGradeDataOwner: JwGradeDataOwner by lazy {
        JwGradeDataOwner(jwGradeStore)
    }

    /** 方案外课程 GPA 策略：仅影响本地派生 GPA，绝不写回学校；默认 UNCONFIRMED。 */
    val academicGpaPolicyStore: com.xmu.course.data.academic.AcademicGpaPolicyStore by lazy {
        com.xmu.course.data.academic.AcademicGpaPolicyStore(appContext)
    }

    val courseRepository: CourseRepository by lazy { CourseRepository(database) }

    val timetableRepository: TimetableRepository by lazy { TimetableRepository(database) }

    /** App 首次引导状态；只包装 onboarding_done，不承载 Settings 或账户状态。 */
    val onboardingPreference: OnboardingPreference by lazy {
        SharedPreferencesOnboardingPreference(appContext)
    }

    /** 冷启动默认落地页偏好（首页/课表）；用户偏好，不进 Room。 */
    val startupDestinationPreference: StartupDestinationPreference by lazy {
        SharedPreferencesStartupDestinationPreference(appContext)
    }

    /** Timetable 页面浏览周偏好；只包装 viewWeek，不接管 current timetable selection。 */
    val timetableViewWeekPreference: ViewWeekPreference by lazy {
        SharedPreferencesViewWeekPreference(appContext)
    }

    /** 时间轴文字样式（Phase 9.1）：全局显示偏好透传给 TimetableViewModel，避免 UI 直接依赖 TimetablePrefs。 */
    val timetableAxisStyle: kotlinx.coroutines.flow.StateFlow<com.xmu.course.data.TimetableAxisStyle>
        get() = TimetablePrefs.axisStyle

    /** 课表功能能力：复用既有仓储与观察契约，供 TimetableViewModel 使用。 */
    val timetableFeatureRepository: TimetableFeatureRepository by lazy {
        TimetableFeatureRepositoryAdapter(
            timetableManagement = timetableRepository,
            timetableObservation = timetableRepository,
            courseManagement = courseRepository,
            observeConfig = timetableRepository::observeConfig,
            saveConfig = timetableRepository::updateConfig,
            tronCourseObservation = tronCourseCacheRepository,
            onCurrentTimetableResolved = { id -> TimetablePrefs.setCurrent(appContext, id) },
        )
    }

    val settings: SettingsDependencies by lazy {
        SettingsDependencies(
            repository = courseRepository,
            assignmentSyncSettings = SharedPreferencesAssignmentSyncSettings(appContext),
            todoAutoSyncSettings = SharedPreferencesTodoAutoSyncSettings(appContext),
        )
    }

    val grades: GradesDependencies by lazy {
        GradesDependencies(
            transcriptReader = CompositeTranscriptReader(
                readers = listOf(
                    AcademicRecordTranscriptReader(academicRecordRepository),
                    ManualTranscriptReader(manualTranscriptRepository),
                ),
            ),
            academicRecordSandboxProvider = academicRecordSandboxProvider,
            academicCompletionStore = academicCompletionStore,
            jwGradeStore = jwGradeStore,
            academicGpaPolicyStore = academicGpaPolicyStore,
        )
    }

    /** Academic Import 通道使用的临时 Excel 数据源；不保存原始 PDF，也不再供 GPA 模拟使用。 */
    val temporaryAcademicXlsxStore: TemporaryAcademicXlsxStore by lazy {
        TemporaryAcademicXlsxStore(
            appContext.cacheDir.resolve("academic-import/academic-import.xlsx"),
        )
    }

    /** Academic Import 独立能力；不并入 Grades，也不复用 JW ImportCoordinator。 */
    val academicImport: AcademicImportDependencies by lazy {
        AcademicImportDependencies(
            pdfTextExtractor = PdfBoxTextExtractor(appContext),
            reportParser = AcademicReportParser(),
            reviewMapper = AcademicImportReviewMapper(),
            decisionMapper = AcademicImportDecisionMapper(),
            sandboxMapper = AcademicImportSandboxMapper(),
            academicRecordImportSaver = AcademicRecordImportSaver(academicRecordRepository),
            temporaryXlsxStore = temporaryAcademicXlsxStore,
        )
    }

    val importCoordinator: ImportCoordinator by lazy {
        ImportCoordinator(
            courseImportProvider = CourseImportProviderAdapter(courseRepository),
            timetableRepository = timetableRepository,
            sessionMarker = SharedPreferencesWiseduSessionMarker(appContext),
        )
    }

    val tronCourseCacheRepository: TronCourseCacheRepository by lazy {
        TronCourseCacheRepository(database.tronCourseDao())
    }

    val update: UpdateDependencies by lazy {
        val settings = SharedPreferencesUpdateSettings(appContext)
        UpdateDependencies(
            repository = GitHubUpdateRepositoryFactory.create(appContext, settings),
            settings = settings,
        )
    }

    val tronClass: TronClassDependencies by lazy {
        val store = EncryptedTronSessionStore(appContext)
        val provider = StoreSessionProvider(store)
        val service = TronClassApiClient(
            sessionProvider = provider,
            requestAuthenticator = SessionCookieAuthenticator(),
        ).service
        val assignmentRepository = AssignmentRepository(
            api = service,
            sessionProvider = provider,
            todoDao = database.todoDao(),
            database = database,
        )
        val officialTodoRepository = OfficialTodoRepository(
            api = service,
            sessionProvider = provider,
            todoDao = database.todoDao(),
            database = database,
        )
        val repository = TronClassRepository(
            api = service,
            sessionProvider = provider,
            courseDao = database.tronCourseDao(),
            sessionStore = store,
            assignmentRepository = assignmentRepository,
            officialTodoRepository = officialTodoRepository,
        )
        TronClassDependencies(
            sessionStore = store,
            sessionProvider = provider,
            assignmentRepository = assignmentRepository,
            officialTodoRepository = officialTodoRepository,
            repository = repository,
            featureRepository = TronClassFeatureRepositoryAdapter(repository),
        )
    }

    val todo: TodoDependencies by lazy {
        val tron = tronClass
        val refreshCoordinator = TronClassTodoRefreshCoordinator(tron.repository)
        val repository = TodoRepository(
            todoDao = database.todoDao(),
            courseDao = database.courseDao(),
            tronCourseDao = database.tronCourseDao(),
        )
        TodoDependencies(
            repository = repository,
            featureRepository = TodoFeatureRepositoryAdapter(repository),
            refreshCoordinator = refreshCoordinator,
            autoSyncCoordinator = TodoAutoSyncCoordinator(
                refreshCoordinator = refreshCoordinator,
                settings = SharedPreferencesTodoAutoSyncSettings(appContext),
                isSessionAvailable = tron.repository::hasSession,
            ),
        )
    }
}
