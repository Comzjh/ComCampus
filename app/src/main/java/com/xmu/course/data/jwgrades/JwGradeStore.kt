package com.xmu.course.data.jwgrades

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * cjcx 成绩单的应用私有本地存储（纯文件，不进 Room，零 schema 迁移）。
 *
 * 数据位置：filesDir/jw_grades/cjcx_grades.json（应用私有目录）。
 * 只持久化白名单业务字段与来源元数据；不含身份、凭据、URL。
 *
 * 原子持久化：先写 tmp 并 fsync，再 move 替换；保存异常时旧快照保持可读、
 * 内存状态不变（刷新失败绝不破坏可用缓存）。启动读取失败进入 StorageFailure。
 */
class JwGradeStore(
    private val file: File,
) {
    sealed interface State {
        data object Empty : State
        data class Loaded(val snapshot: JwGradeSnapshot) : State
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
            _state.value = State.Loaded(JwGradeSnapshotCodec.decode(file.readText()))
        } catch (error: Exception) {
            _state.value = State.StorageFailure("本机成绩缓存无法读取，数据可能已损坏；可删除后重新刷新")
        }
    }

    /** 用一次成功刷新的快照整体替换缓存（无逐行合并、无静默保留旧行）。 */
    fun replace(snapshot: JwGradeSnapshot): Boolean {
        require(snapshot.schemaVersion == JwGradeSnapshot.SCHEMA_VERSION) {
            "replace requires current internal schema"
        }
        return persist(snapshot)
    }

    /** 删除本机全部 cjcx 成绩缓存。由隐私数据属主与页面删除入口共用。 */
    fun clearAll(): Boolean {
        val fileDeleted = deleteIfExists(file)
        val tempDeleted = deleteIfExists(tempFile())
        val cleared = fileDeleted && tempDeleted
        _state.value = if (cleared) {
            State.Empty
        } else {
            State.StorageFailure("本机成绩缓存未能完全清除，请重试")
        }
        return cleared
    }

    fun current(): State = _state.value

    private fun persist(snapshot: JwGradeSnapshot): Boolean {
        val temp = tempFile()
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                check(parent.mkdirs() || parent.exists()) { "cannot create storage directory" }
            }
            FileOutputStream(temp).use { output ->
                output.write(JwGradeSnapshotCodec.encode(snapshot).toByteArray())
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

    /** 优先原子 move；文件系统不支持时退化为替换 move（与 AcademicCompletionStore 同规则）。 */
    private fun moveOver(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
