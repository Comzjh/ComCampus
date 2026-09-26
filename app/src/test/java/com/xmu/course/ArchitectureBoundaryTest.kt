package com.xmu.course

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Package-level dependency guard for the current single-module migration.
 *
 * Widget is an independent Android entry point and is intentionally excluded;
 * its Room access is guarded by WidgetDataSourceTest instead.
 */
class ArchitectureBoundaryTest {

    @Test
    fun uiDoesNotCreateRoomOrNetworkClients() {
        val uiRoot = sourceRoot("com", "xmu", "course", "ui")
        val violations = kotlinFiles(uiRoot)
            .filterNot { it.toString().contains("ui${java.io.File.separator}widget") }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    when {
                        "AppDatabase" in line -> "${file}:${index + 1}: AppDatabase"
                        "Retrofit.Builder" in line -> "${file}:${index + 1}: Retrofit.Builder"
                        "OkHttpClient" in line -> "${file}:${index + 1}: OkHttpClient"
                        else -> null
                    }
                }
            }

        assertTrue(
            "UI must depend on contracts/composition boundaries, not Room or network clients: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun viewModelsDoNotCreateRepositoryImplementations() {
        val uiRoot = sourceRoot("com", "xmu", "course", "ui")
        val repositoryConstruction = repositoryConstructionRegex()
        val violations = kotlinFiles(uiRoot)
            .filter { it.fileName.toString().endsWith("ViewModel.kt") }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (repositoryConstruction.containsMatchIn(line)) {
                        listOf("${file}:${index + 1}: $line")
                    } else {
                        emptyList()
                    }
                }.flatten()
            }

        assertTrue(
            "ViewModels must receive repository contracts from a boundary: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun screensAndFactoriesDoNotCreateRepositoryImplementations() {
        val uiRoot = sourceRoot("com", "xmu", "course", "ui")
        val repositoryConstruction = repositoryConstructionRegex()
        val violations = kotlinFiles(uiRoot)
            .filterNot { it.toString().contains("ui${java.io.File.separator}widget") }
            .filterNot { it.fileName.toString().endsWith("ViewModel.kt") }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (repositoryConstruction.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Screens and factories must receive repository contracts from a boundary: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun uiDoesNotAccessDatabaseOrDaoTypes() {
        val uiRoot = sourceRoot("com", "xmu", "course", "ui")
        val forbiddenTypes = listOf("AppDatabase") + daoTypeNames()
        val forbiddenTypePattern = Regex(
            "\\b(?:${forbiddenTypes.joinToString("|") { Regex.escape(it) }})\\b",
        )
        val violations = kotlinFiles(uiRoot)
            .filterNot { it.toString().contains("ui${java.io.File.separator}widget") }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (forbiddenTypePattern.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "UI must access local data through repositories/contracts, not Room or DAO types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun uiDoesNotCreateNetworkApiImplementations() {
        val uiRoot = sourceRoot("com", "xmu", "course", "ui")
        val networkConstruction = networkConstructionRegex()
        val violations = kotlinFiles(uiRoot)
            .filterNot { it.toString().contains("ui${java.io.File.separator}widget") }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (networkConstruction.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "UI must receive network clients/services from a data boundary: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun dataDoesNotDependOnUiPackages() {
        val dataRoot = sourceRoot("com", "xmu", "course", "data")
        val violations = kotlinFiles(dataRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    val importedUiType = line.contains("import com.xmu.course.ui.")
                    val importedComposeType = line.contains("import androidx.compose.")
                    val importedViewModel = line.contains("import androidx.lifecycle.ViewModel")
                    if (importedUiType || importedComposeType || importedViewModel) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Data packages must not depend on UI, Compose, or ViewModel types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun domainModuleDoesNotDependOnAppLayers() {
        val domainRoot = moduleSourceRoot("domain")
        val violations = forbiddenImports(
            domainRoot,
            listOf("import android.", "import androidx.", "import com.xmu.course.data.", "import com.xmu.course.ui."),
        )

        assertTrue(
            "Domain module must remain independent from Android, data, and UI layers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun coreContractsModuleDoesNotDependOnAppLayers() {
        val contractsRoot = moduleSourceRoot("core-contracts")
        val violations = forbiddenImports(
            contractsRoot,
            listOf(
                "import android.",
                "import androidx.",
                "import com.xmu.course.data.",
                "import com.xmu.course.ui.",
            ),
        )

        assertTrue(
            "Core contracts module must remain independent from Android, data, and UI layers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun importPackageDoesNotConstructRepositoryImplementations() {
        val importRoot = sourceRoot("com", "xmu", "course", "data", "import")
        val repositoryConstruction = repositoryConstructionRegex()
        val violations = kotlinFiles(importRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (repositoryConstruction.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Import orchestration must receive repository contracts and not construct implementations: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoUiDoesNotCreateTodoImplementations() {
        val todoUiRoot = sourceRoot("com", "xmu", "course", "ui", "todo")
        val todoConstruction = todoImplementationConstructionRegex()
        val violations = kotlinFiles(todoUiRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (todoConstruction.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Todo UI must receive implementations from AppContainer.todo, not construct them: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoUiWritePathDoesNotConstructDataModelsOrDaos() {
        val todoUiRoot = sourceRoot("com", "xmu", "course", "ui", "todo")
        val todoConstruction = todoImplementationConstructionRegex()
        val dataModelConstruction = Regex("\\bTodoEntity\\s*\\(")
        val daoReference = Regex("\\bTodoDao\\b")
        val violations = kotlinFiles(todoUiRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    val codeLine = line.trim()
                    if (codeLine.startsWith("//") || codeLine.startsWith("*") ||
                        codeLine.startsWith("/*")
                    ) {
                        null
                    } else if (todoConstruction.containsMatchIn(codeLine) ||
                        dataModelConstruction.containsMatchIn(codeLine) ||
                        daoReference.containsMatchIn(codeLine)
                    ) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Todo UI write paths must use ids/commands and not construct data models or DAOs: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoStateDisplayModelDoesNotExposeRoomEntity() {
        val file = sourceRoot("com", "xmu", "course", "ui", "todo")
            .resolve("TodoState.kt")
        val source = Files.readAllLines(file).joinToString("\n")

        assertFalse(
            "TodoState display models must not expose TodoEntity",
            source.contains("TodoEntity"),
        )
    }

    @Test
    fun todoUiDoesNotImportTodoEntity() {
        val todoUiRoot = sourceRoot("com", "xmu", "course", "ui", "todo")
        val violations = kotlinFiles(todoUiRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (line.contains("TodoEntity")) "${file}:${index + 1}: $line" else null
                }
            }

        assertTrue(
            "Todo UI must use feature models and commands, not Room entities: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoFeatureContractDoesNotDependOnDataImplementation() {
        val featureRoot = sourceRoot("com", "xmu", "course", "data", "todo", "feature")
        val forbidden = listOf(
            "TodoEntity",
            "TodoDao",
            "com.xmu.course.data.todo.TodoRepository",
            "TodoSource",
            "UpdateTodoCommand",
            "android.",
            "androidx.",
            "com.xmu.course.data.local.",
            "com.xmu.course.data.tronclass.",
        )
        val violations = kotlinFiles(featureRoot)
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
                }
            }

        assertTrue(
            "Todo feature contract must remain independent from data implementations: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoViewModelUsesRefreshContractNotLegacyCoordinator() {
        val file = sourceRoot("com", "xmu", "course", "ui", "todo")
            .resolve("TodoViewModel.kt")
        val lines = Files.readAllLines(file)

        assertTrue(
            "TodoViewModel must consume TodoRefreshReader",
            lines.any { "import com.xmu.course.contracts.todo.TodoRefreshReader" in it },
        )
        assertFalse(
            "TodoViewModel manual refresh must not import TodoRefreshCoordinator",
            lines.any { "import com.xmu.course.data.todo.TodoRefreshCoordinator" in it },
        )
    }

    @Test
    fun todoFeatureModelConsumersUseContractOwnedModel() {
        val files = listOf(
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoViewModel.kt"),
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoState.kt"),
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoScreen.kt"),
            moduleSourceRoot("core-contracts").resolve(Paths.get("com", "xmu", "course", "contracts", "todo"))
                .resolve("TodoFeatureRepository.kt"),
            sourceRoot("com", "xmu", "course", "data", "todo", "adapter")
                .resolve("TodoFeatureRepositoryAdapter.kt"),
            sourceRoot("com", "xmu", "course", "data", "todo").resolve("TodoFeatureMapper.kt"),
        )
        val violations = files.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.contains("import com.xmu.course.data.todo.model.TodoFeatureModel")) {
                    "${file}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Todo Feature model consumers must use the contract-owned model: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun todoFeatureConsumersUseContractOwnedCourseOptionModel() {
        val files = listOf(
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoViewModel.kt"),
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoState.kt"),
            sourceRoot("com", "xmu", "course", "ui", "todo").resolve("TodoScreen.kt"),
            moduleSourceRoot("core-contracts").resolve(Paths.get("com", "xmu", "course", "contracts", "todo"))
                .resolve("TodoFeatureRepository.kt"),
            sourceRoot("com", "xmu", "course", "data", "todo", "adapter")
                .resolve("TodoFeatureRepositoryAdapter.kt"),
            sourceRoot("com", "xmu", "course", "data", "todo").resolve("TodoFeatureMapper.kt"),
        )
        val violations = files.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.contains("import com.xmu.course.data.todo.model.TodoCourseOptionModel")) {
                    "${file}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Todo Feature consumers must use the contract-owned course option model: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun removedTodoCompatibilityFilesDoNotExist() {
        val legacyRoot = sourceRoot("com", "xmu", "course", "data", "todo", "model")
        val legacyFiles = listOf(
            legacyRoot.resolve("TodoFeatureModel.kt"),
            legacyRoot.resolve("TodoCourseOptionModel.kt"),
            legacyRoot.resolve("TodoSourceUi.kt"),
        )
        val remaining = legacyFiles.filter { Files.exists(it) }

        assertTrue(
            "Deprecated Todo compatibility files must be removed: $remaining",
            remaining.isEmpty(),
        )
    }

    @Test
    fun legacyTodoWriteCommandsAreConsumedOnlyByAdapter() {
        val productionRoot = sourceRoot("com", "xmu", "course")
        val files = kotlinFiles(productionRoot)
            .filterNot { it.toString().contains("src${java.io.File.separator}test") }
        val violations = files.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.contains("import com.xmu.course.data.todo.model.CreateTodoCommand") ||
                    line.contains("import com.xmu.course.data.todo.model.EditTodoCommand")
                ) {
                    val adapterPath = "data${java.io.File.separator}todo${java.io.File.separator}adapter"
                    if (!file.toString().contains(adapterPath)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Legacy Todo write commands must remain Adapter-only: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun settingsViewModelDoesNotConstructPreferenceImplementations() {
        val file = sourceRoot("com", "xmu", "course", "ui", "settings")
            .resolve("SettingsViewModel.kt")
        val forbidden = Regex("\\bSharedPreferences[A-Za-z0-9_]*Settings\\s*\\(")
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (forbidden.containsMatchIn(line)) "${file}:${index + 1}: $line" else null
        }

        assertTrue(
            "Settings UI must receive preference implementations from AppContainer.settings: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun tronClassUiDoesNotExposeRoomCourseEntity() {
        val files = listOf(
            sourceRoot("com", "xmu", "course", "ui", "tronclass")
                .resolve("TronClassUiState.kt"),
            sourceRoot("com", "xmu", "course", "ui", "tronclass")
                .resolve("TronClassScreen.kt"),
        )
        val violations = files.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (Regex("\\bTronCourseEntity\\b").containsMatchIn(line)) {
                    "${file}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "TronClass UI state and screens must use TronCourseUiModel, not the Room entity: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun tronClassUiStateDoesNotExposeIntegrationError() {
        val file = sourceRoot("com", "xmu", "course", "ui", "tronclass")
            .resolve("TronClassUiState.kt")
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (Regex("\\bTronClassError\\b").containsMatchIn(line)) {
                "${file}:${index + 1}: $line"
            } else {
                null
            }
        }

        assertTrue(
            "TronClassUiState must use TronClassUiError, not the integration error model: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun migratedManagerViewModelsDoNotReintroduceConcreteContracts() {
        val checks = listOf(
            sourceRoot("com", "xmu", "course", "ui", "manager") to
                ("TimetableManagerViewModel.kt" to "TimetableRepository"),
            sourceRoot("com", "xmu", "course", "ui", "course") to
                ("CourseManagerViewModel.kt" to "CourseRepository"),
        )
        val violations = checks.flatMap { (root, check) ->
            val (fileName, forbiddenType) = check
            kotlinFiles(root)
                .filter { it.fileName.toString() == fileName }
                .flatMap { file ->
                    Files.readAllLines(file).mapIndexedNotNull { index, line ->
                        val codeLine = line.trim()
                        if (!codeLine.startsWith("//") &&
                            !codeLine.startsWith("*") &&
                            !codeLine.startsWith("/*") &&
                            Regex("\\b${Regex.escape(forbiddenType)}\\b").containsMatchIn(codeLine)
                        ) {
                            "${file}:${index + 1}: $line"
                        } else {
                            null
                        }
                    }
                }
        }

        assertTrue(
            "Migrated Manager ViewModels must depend on management contracts, not concrete repositories: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun migratedViewModelsDoNotReintroduceConcreteRepositories() {
        val checks = listOf(
            sourceRoot("com", "xmu", "course", "ui", "timetable") to
                ("TimetableViewModel.kt" to "TronCourseCacheRepository"),
            sourceRoot("com", "xmu", "course", "ui", "manager") to
                ("TimetableManagerViewModel.kt" to "TimetableRepository"),
            sourceRoot("com", "xmu", "course", "ui", "course") to
                ("CourseManagerViewModel.kt" to "CourseRepository"),
        )
        val violations = checks.flatMap { (root, check) ->
            val (fileName, forbiddenType) = check
            kotlinFiles(root)
                .filter { it.fileName.toString() == fileName }
                .flatMap { file ->
                    Files.readAllLines(file).mapIndexedNotNull { index, line ->
                        val codeLine = line.trim()
                        if (!codeLine.startsWith("//") &&
                            !codeLine.startsWith("*") &&
                            !codeLine.startsWith("/*") &&
                            Regex("\\b${Regex.escape(forbiddenType)}\\b").containsMatchIn(codeLine)
                        ) {
                            "${file}:${index + 1}: $line"
                        } else {
                            null
                        }
                    }
                }
        }

        assertTrue(
            "Migrated ViewModels must depend on capability contracts, not concrete repositories: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun timetableViewModelDoesNotReintroduceDataOrIntegrationOrchestration() {
        val file = sourceRoot("com", "xmu", "course", "ui", "timetable")
            .resolve("TimetableViewModel.kt")
        val forbidden = listOf(
            "TimetableRepository",
            "CourseRepository",
            "CourseMatcher",
            "CourseMatchMapper",
            "TronCourseObservationContract",
            "TronCourseEntity",
            "TimetableObservationContract",
            "TimetableManagementContract",
            "TimetableImportContract",
            "CourseManagementContract",
            "TimetableMatchUiModel",
            "data.tronclass.matcher",
            "TimetablePrefs",
        )
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (line.trimStart().startsWith("import ") && forbidden.any(line::contains)) {
                "${file}:${index + 1}: $line"
            } else {
                null
            }
        }

        assertTrue(
            "TimetableViewModel must depend on TimetableFeatureRepository, not data/integration orchestration: $violations",
            violations.isEmpty(),
        )
        assertTrue(
            "TimetableViewModel must import TimetableFeatureRepository",
            Files.readAllLines(file).any { "import com.xmu.course.contracts.timetable.TimetableFeatureRepository" in it },
        )
        assertTrue(
            "TimetableViewModel must import ViewWeekPreference",
            Files.readAllLines(file).any { "import com.xmu.course.contracts.timetable.ViewWeekPreference" in it },
        )
    }

    @Test
    fun appRootDoesNotPersistOnboardingDirectly() {
        val file = sourceRoot("com", "xmu", "course", "ui")
            .resolve("XmuCourseApp.kt")
        val lines = Files.readAllLines(file)

        assertTrue(
            "XmuCourseApp must consume the onboarding capability",
            lines.any { "appContainer.onboardingPreference" in it },
        )
        assertTrue(
            "XmuCourseApp must not access the onboarding SharedPreferences key directly",
            lines.none { "onboarding_done" in it || "getSharedPreferences(\"app_prefs\"" in it },
        )
    }

    @Test
    fun onboardingStorageHasOneProductionOwner() {
        val mainRoot = sourceRoot()
        val owner = mainRoot.resolve(
            Paths.get(
                "com",
                "xmu",
                "course",
                "data",
                "presentation",
                "SharedPreferencesOnboardingPreference.kt",
            ),
        ).normalize()
        val violations = kotlinFiles(mainRoot)
            .filterNot { it.normalize() == owner }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    val trimmed = line.trimStart()
                    val isComment = trimmed.startsWith("//") ||
                        trimmed.startsWith("/*") ||
                        trimmed.startsWith("*")
                    if (!isComment && ("onboarding_done" in line || "app_prefs" in line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Onboarding storage must have one production owner: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun coreImportContractsDoNotDependOnDataTransport() {
        val importsRoot = moduleSourceRoot("core-contracts").resolve(
            Paths.get("com", "xmu", "course", "contracts", "imports"),
        )
        val forbidden = listOf(
            "CourseImportInput",
            "ImportPreparation",
            "CourseImportProvider",
            "com.xmu.course.data.import",
        )
        val violations = kotlinFiles(importsRoot).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (forbidden.any(line::contains)) {
                    "${file}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Core import contracts must not depend on data import transport: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun coreImportResultHasOneProductionMapper() {
        val importRoot = sourceRoot("com", "xmu", "course", "data", "import")
        val mapper = importRoot.resolve(
            Paths.get("adapter", "CourseImportResultMapper.kt"),
        ).normalize()
        val references = kotlinFiles(importRoot).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (line.trimStart().startsWith("import ") &&
                    "com.xmu.course.contracts.imports.CourseImportResult" in line
                ) {
                    Triple(file.normalize(), index + 1, line)
                } else {
                    null
                }
            }
        }
        val unexpected = references.filter { it.first != mapper }

        assertTrue(
            "CourseImportResult must be produced through one data mapper: $unexpected",
            unexpected.isEmpty(),
        )
        assertTrue(
            "CourseImportResult mapper must remain present",
            references.any { it.first == mapper },
        )
    }

    @Test
    fun importConflictPresentationModelDoesNotExposeDataTransport() {
        val file = sourceRoot("com", "xmu", "course", "ui", "import", "model")
            .resolve("ImportConflictModel.kt")
        val forbidden = listOf(
            "ImportPreparation",
            "ImportResult",
            "CourseImportInput",
            "CourseImportProvider",
            "WebView",
            "Cookie",
            "android.",
            "androidx.",
        )
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
        }

        assertTrue(
            "Import conflict presentation model must stay provider-neutral: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun importUiStateUsesPresentationConflictModel() {
        val file = sourceRoot("com", "xmu", "course", "ui", "import")
            .resolve("ImportViewModel.kt")
        val lines = Files.readAllLines(file)

        assertTrue(
            "ImportUiState must expose ImportConflictModel",
            lines.any { "val conflict: ImportConflictModel?" in it },
        )
        assertTrue(
            "ImportUiState must not expose ImportPreparation.Conflict",
            lines.none { "val conflict: ImportPreparation.Conflict" in it },
        )
    }

    @Test
    fun importScreensUseStartDatePresentationSignal() {
        val files = listOf(
            sourceRoot("com", "xmu", "course", "ui", "import").resolve("ImportScreen.kt"),
            sourceRoot("com", "xmu", "course", "ui", "import").resolve("WebViewScreen.kt"),
        )
        val violations = files.flatMap { file ->
            val lines = Files.readAllLines(file)
            buildList {
                if (lines.none { "shouldShowStartDatePicker" in it }) {
                    add("$file does not consume shouldShowStartDatePicker")
                }
                lines.mapIndexedNotNullTo(this) { index, line ->
                    if ("pendingStartDate" in line) "$file:${index + 1}: $line" else null
                }
            }
        }

        assertTrue(
            "Import screens must consume a presentation signal, not PendingStartDate payload: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun importViewModelKeepsPendingStartDatePrivate() {
        val file = sourceRoot("com", "xmu", "course", "ui", "import")
            .resolve("ImportViewModel.kt")
        val lines = Files.readAllLines(file)

        assertTrue(
            "ImportViewModel must keep pending start-date payload private",
            lines.any { "private var pendingStartDate: PendingStartDate?" in it },
        )
        assertTrue(
            "ImportUiState must expose the start-date presentation signal",
            lines.any { "shouldShowStartDatePicker" in it },
        )
    }

    @Test
    fun timetableFeatureContractDoesNotExposeIntegrationInternals() {
        val featureRoots = listOf(
            sourceRoot("com", "xmu", "course", "data", "timetable", "feature"),
            moduleSourceRoot("core-contracts").resolve(
                Paths.get("com", "xmu", "course", "contracts", "timetable", "model"),
            ),
        )
        val forbidden = listOf(
            "TimetableEntity",
            "CourseEntity",
            "TronCourseEntity",
            "TimetableMatchUiModel",
            "data.tronclass.matcher",
            "MatchResult",
            "MatchStrategy",
            "CourseMatcher",
            "TimetableDao",
            "SharedPreferences",
            "android.",
            "androidx.",
        )
        val violations = featureRoots.flatMap { featureRoot ->
            kotlinFiles(featureRoot).flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
                }
            }
        }

        assertTrue(
            "Timetable feature contract must not expose storage or integration internals: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun timetableScreenDoesNotImportDataImplementation() {
        val file = sourceRoot("com", "xmu", "course", "ui", "timetable")
            .resolve("TimetableScreen.kt")
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (line.trimStart().startsWith("import com.xmu.course.data.")) {
                "${file}:${index + 1}: $line"
            } else {
                null
            }
        }

        assertTrue(
            "TimetableScreen must consume UI/domain state, not data implementations: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun authCenterFactoryUsesAppContainerSessionStore() {
        val factory = sourceRoot("com", "xmu", "course", "ui", "auth")
            .resolve("AuthCenterViewModel.kt")
        val lines = Files.readAllLines(factory)
        val directStoreConstruction = lines.mapIndexedNotNull { index, line ->
            if ("EncryptedTronSessionStore" in line && "import " !in line) {
                "${factory}:${index + 1}: $line"
            } else {
                null
            }
        }

        assertTrue(
            "AuthCenterViewModelFactory must not construct its own session store: $directStoreConstruction",
            directStoreConstruction.isEmpty(),
        )
        assertTrue(
            "AuthCenterViewModelFactory must reuse the AppContainer TronClass session store",
            lines.any { "appContainer.tronClass.sessionStore" in it },
        )
    }

    @Test
    fun integrationConstructorsStayInDataOrDiPackages() {
        val sourceRoot = sourceRoot("com", "xmu", "course")
        val integrationConstruction = Regex(
            "\\b(?:[A-Z]\\w*(?:Repository|ApiClient|ApiService)|AppDatabase|Retrofit\\.Builder|OkHttpClient\\.Builder)\\s*\\(",
        )
        val violations = kotlinFiles(sourceRoot)
            .filterNot { file ->
                val normalized = file.toString().replace('\\', '/')
                "/data/" in normalized || "/di/" in normalized
            }
            .flatMap { file ->
                Files.readAllLines(file).mapIndexedNotNull { index, line ->
                    if (integrationConstruction.containsMatchIn(line)) {
                        "${file}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Integration constructors must stay in data or di packages: $violations",
            violations.isEmpty(),
        )
    }

    private fun sourceRoot(vararg parts: String): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java", *parts),
            Paths.get("src", "main", "java", *parts),
        )
        return candidates.firstOrNull(Files::isDirectory)
            ?: error("Cannot locate UI source root from ${Paths.get("").toAbsolutePath()}")
    }

    private fun moduleSourceRoot(module: String): Path {
        val candidates = listOf(
            Paths.get(module, "src", "main", "kotlin"),
            Paths.get("..", module, "src", "main", "kotlin"),
        )
        return candidates.firstOrNull(Files::isDirectory)
            ?: error("Cannot locate $module source root from ${Paths.get("").toAbsolutePath()}")
    }

    private fun forbiddenImports(root: Path, prefixes: List<String>): List<String> = kotlinFiles(root)
        .flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (prefixes.any(line::startsWith)) "${file}:${index + 1}: $line" else null
            }
        }

    private fun kotlinFiles(root: Path): List<Path> = Files.walk(root).use { stream ->
        stream
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .collect(Collectors.toList())
    }

    private fun repositoryConstructionRegex(): Regex {
        val dataRoot = sourceRoot("com", "xmu", "course", "data")
        val repositoryDeclarations = Regex("\\b(?:class|object)\\s+([A-Z]\\w*Repository)\\b")
        val repositoryNames = kotlinFiles(dataRoot)
            .flatMap { file ->
                repositoryDeclarations
                    .findAll(Files.readAllLines(file).joinToString("\n"))
                    .map { it.groupValues[1] }
                    .toList()
            }
            .filterNot { it.endsWith("Contract") }
            .distinct()
            .sorted()

        require(repositoryNames.isNotEmpty()) { "No repository implementations found under $dataRoot" }
        return Regex("\\b(?:${repositoryNames.joinToString("|") { Regex.escape(it) }})\\s*\\(")
    }

    private fun daoTypeNames(): List<String> {
        val localRoot = sourceRoot("com", "xmu", "course", "data", "local")
        val daoDeclaration = Regex("\\binterface\\s+([A-Z]\\w*Dao)\\b")
        return kotlinFiles(localRoot)
            .flatMap { file ->
                daoDeclaration
                    .findAll(Files.readAllLines(file).joinToString("\n"))
                    .map { it.groupValues[1] }
                    .toList()
            }
            .distinct()
            .sorted()
    }

    private fun networkConstructionRegex(): Regex {
        val dataRoot = sourceRoot("com", "xmu", "course", "data")
        val networkDeclaration = Regex(
            "\\b(?:class|object|interface)\\s+([A-Z]\\w*(?:ApiClient|ApiService|Api|Service))\\b",
        )
        val networkNames = kotlinFiles(dataRoot)
            .flatMap { file ->
                networkDeclaration
                    .findAll(Files.readAllLines(file).joinToString("\n"))
                    .map { it.groupValues[1] }
                    .toList()
            }
            .distinct()
            .sorted()

        return if (networkNames.isEmpty()) {
            Regex("a^")
        } else {
            Regex("\\b(?:${networkNames.joinToString("|") { Regex.escape(it) }})\\s*\\(")
        }
    }

    private fun todoImplementationConstructionRegex(): Regex {
        val dataRoot = sourceRoot("com", "xmu", "course", "data")
        val implementationDeclaration = Regex(
            "\\b(?:class|object)\\s+([A-Z]\\w*(?:Repository|Coordinator))\\b",
        )
        val implementationNames = kotlinFiles(dataRoot)
            .flatMap { file ->
                implementationDeclaration
                    .findAll(Files.readAllLines(file).joinToString("\n"))
                    .map { it.groupValues[1] }
                    .filter { it.contains("Todo") }
                    .toList()
            }
            .distinct()
            .sorted()

        require(implementationNames.isNotEmpty()) { "No Todo implementations found under $dataRoot" }
        return Regex("\\b(?:${implementationNames.joinToString("|") { Regex.escape(it) }})\\s*\\(")
    }

    @Test
    fun gradesUiDoesNotDependOnAcademicImportImplementation() {
        val gradesRoot = sourceRoot("com", "xmu", "course", "ui", "grades")
        val forbidden = listOf(
            "com.xmu.course.data.academicimport",
            "PdfTextExtractor",
            "AcademicReportParser",
            "ContentResolver",
        )
        val violations = kotlinFiles(gradesRoot).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
            }
        }

        assertTrue(
            "Grades UI/ViewModel must not depend on Academic Import implementation details: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun academicUiUsesCapabilityInsteadOfParserOrFileApis() {
        // 过渡期的 AcademicImport UI 已退出正式产品：解析器与文件读取细节只能留在 capability 实现里
        val roots = listOf(
            sourceRoot("com", "xmu", "course", "ui", "academic"),
            sourceRoot("com", "xmu", "course", "ui", "academiccompletion"),
            sourceRoot("com", "xmu", "course", "ui", "grades"),
        )
        val forbidden = listOf(
            "PdfTextExtractor",
            "PdfBoxTextExtractor",
            "AcademicReportParser",
            "ContentResolver",
            "AcademicImportViewModel",
        )
        val violations = roots.flatMap { root -> kotlinFiles(root) }.flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
            }
        }

        assertTrue(
            "Academic UI must call the capability, not parser or file APIs: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun academicImportCompositionDoesNotOwnPersistenceNetworkOrAuthentication() {
        val file = sourceRoot("com", "xmu", "course", "di")
            .resolve("AcademicImportDependencies.kt")
        val forbidden = listOf(
            "AppDatabase",
            "Dao",
            "Retrofit",
            "OkHttp",
            "Cookie",
            "Auth",
            "JW",
        )
        val violations = Files.readAllLines(file).mapIndexedNotNull { index, line ->
            if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
        }

        assertTrue(
            "Academic Import composition must not own persistence, network, or auth: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun academicImportDataDoesNotAddPersistenceOrNetworkForFileInput() {
        val root = sourceRoot("com", "xmu", "course", "data", "academicimport")
        val forbidden = listOf(
            "AppDatabase",
            "SharedPreferences",
            "Retrofit",
            "OkHttp",
            "FileOutputStream",
            "writeBytes",
        )
        val violations = kotlinFiles(root).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
            }
        }

        assertTrue(
            "Academic Import file input must remain one-shot and local without persistence/network: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun tutorialUiIsPureUi() {
        val tutorialRoot = sourceRoot("com", "xmu", "course", "ui", "tutorial")
        val forbidden = listOf(
            "com.xmu.course.data.",
            "com.xmu.course.network.",
            "com.xmu.course.repository.",
            "AppDatabase",
            "Retrofit",
            "OkHttpClient",
        )
        val violations = kotlinFiles(tutorialRoot).flatMap { file ->
            Files.readAllLines(file).mapIndexedNotNull { index, line ->
                if (forbidden.any(line::contains)) "${file}:${index + 1}: $line" else null
            }
        }

        assertTrue(
            "Tutorial UI must stay pure presentation (no data/network/repository access): $violations",
            violations.isEmpty(),
        )
    }

}
