package com.xmu.course.data.academiccompletion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 学业完成快照的应用私有本地存储（纯文件，不进 Room，零 schema 迁移）。
 *
 * 数据位置：filesDir/academic_completion/snapshot.json（应用私有目录）。
 * 只持久化脱敏后的内部快照与本地覆盖；来源原始文件（含姓名/学号）不落盘、不复制。
 *
 * 原子持久化：先写 snapshot.json.tmp 并 fsync，再 move 替换正式文件；
 * 保存异常时旧快照保持可读，内存状态不变。启动读取失败进入 StorageFailure，
 * 不 crash，也不静默伪造空数据。
 */
class AcademicCompletionStore(
    private val file: File,
) {
    sealed interface State {
        data object Empty : State
        data class Loaded(val snapshot: AcademicCompletionSnapshot) : State
        data class StorageFailure(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Empty)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) {
            _state.value = State.Empty
            return
        }
        try {
            val snapshot = AcademicCompletionSnapshotCodec.decode(file.readText())
            _state.value = State.Loaded(snapshot)
        } catch (error: Exception) {
            _state.value = State.StorageFailure("本机快照无法读取，数据可能已损坏；可在「数据管理」中删除后重新刷新")
        }
    }

    /**
     * 导入新快照。重新导入时的覆盖优先级（审查决议 RC-7）：
     * - 新快照该课已有来源确认学分 => 来源事实优先，丢弃对应本地覆盖；
     * - 新快照该课仍 NEEDS_MANUAL 且课程代码精确一致 => 保留本地覆盖；
     * - 课程消失或无精确匹配 => 丢弃覆盖（绝不模糊迁移）。
     */
    fun import(snapshot: AcademicCompletionSnapshot): Boolean {
        require(snapshot.schemaVersion == ACADEMIC_COMPLETION_INTERNAL_SCHEMA_VERSION) {
            "import requires current internal schema"
        }
        val existing = (current() as? State.Loaded)?.snapshot?.localOverrides.orEmpty()
        val carried = existing.filter { (courseCode, _) ->
            val course = snapshot.enrolledCourses.firstOrNull { it.courseCode == courseCode }
            course != null && course.status == SourceXfStatus.NEEDS_MANUAL && course.creditsText == null
        }
        return persist(snapshot.copy(localOverrides = carried))
    }

    /**
     * 写入一门课的本地学分确认。
     * 仅接受来源为 NEEDS_MANUAL 的在修课程；绝不修改任何来源事实字段。
     */
    fun putLocalCreditOverride(courseCode: String, creditsText: String): Boolean {
        val loaded = current() as? State.Loaded ?: return false
        val snapshot = loaded.snapshot
        val course = snapshot.enrolledCourses.firstOrNull { it.courseCode == courseCode } ?: return false
        if (course.status != SourceXfStatus.NEEDS_MANUAL || course.creditsText != null) return false
        val normalized = CreditsDecimal.normalizePositive(creditsText) ?: return false
        val next = snapshot.copy(
            localOverrides = snapshot.localOverrides + (courseCode to LocalCreditOverride(courseCode, normalized)),
        )
        return persist(next)
    }

    /** 删除本机全部学业完成数据（快照 + 本地覆盖）。由隐私数据属主与页面删除入口共用。 */
    fun clearAll(): Boolean {
        val fileDeleted = deleteIfExists(file)
        val tempDeleted = deleteIfExists(tempFile())
        val cleared = fileDeleted && tempDeleted
        _state.value = if (cleared) {
            State.Empty
        } else {
            State.StorageFailure("本机快照未能完全清除，请重试")
        }
        return cleared
    }

    fun current(): State = _state.value

    private fun persist(snapshot: AcademicCompletionSnapshot): Boolean {
        val temp = tempFile()
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                check(parent.mkdirs() || parent.exists()) { "cannot create storage directory" }
            }
            FileOutputStream(temp).use { output ->
                output.write(AcademicCompletionSnapshotCodec.encode(snapshot).toByteArray())
                output.flush()
                output.fd.sync()
            }
            moveOver(temp, file)
            _state.value = State.Loaded(snapshot)
            true
        } catch (_: Exception) {
            deleteIfExists(temp)
            false
        }
    }

    private fun tempFile(): File = File(file.parentFile, file.name + ".tmp")

    private fun deleteIfExists(target: File): Boolean = try {
        !target.exists() || (target.delete() && !target.exists())
    } catch (_: Exception) {
        false
    }

    /** 优先原子 move；文件系统不支持时退化为替换 move（Windows/Robolectric 目标文件已存在时 renameTo 会失败）。 */
    private fun moveOver(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
