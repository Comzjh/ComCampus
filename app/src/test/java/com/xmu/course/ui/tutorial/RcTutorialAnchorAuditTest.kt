package com.xmu.course.ui.tutorial

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D6 锁定：教程锚点三方一致。
 *
 * `TutorialTargetKey` 声明的 key、页面里真正调用了 `tutorialTarget(...)` 的 key、
 * `TutorialRegistry` 步骤引用的 key，必须完全相等。
 * 多出来的一侧都是孤儿：注册了却没锚点 = 走到那一步就报“找不到目标”（BUG-09）；
 * 绑定了却没人教 = 白占布局与语义。
 */
class RcTutorialAnchorAuditTest {

    private val keyValues: Map<String, String> by lazy {
        TutorialTargetKey::class.java.fields
            .filter { it.type == String::class.java }
            .associate { it.name to it.get(null) as String }
    }

    private val declaredKeys: Set<String> by lazy { keyValues.values.toSet() }

    private val anchoredKeys: Set<String> by lazy {
        val regex = Regex("tutorialTarget\\(TutorialTargetKey\\.([A-Z_]+)\\)")
        val names = mainSources().flatMap { file ->
            regex.findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }
        }.toSet()
        val unknown = names - keyValues.keys
        assertTrue("anchors must use a declared TutorialTargetKey constant: " + unknown, unknown.isEmpty())
        names.map { keyValues.getValue(it) }.toSet()
    }

    private val registeredKeys: Set<String> by lazy {
        TutorialRegistry.ids()
            .flatMap { requireNotNull(TutorialRegistry.get(it)).steps }
            .mapNotNull { it.targetKey }
            .toSet()
    }

    private fun mainSources(): List<File> {
        val dir = File(projectRoot(), "app/src/main/java")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun projectRoot(): File {
        var dir = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            if (File(dir, "app/src/main/java").isDirectory) return dir
            dir = checkNotNull(dir.parentFile)
        }
        error("project root not found")
    }

    @Test
    fun `every declared tutorial key is actually anchored`() {
        assertEquals(declaredKeys, anchoredKeys)
    }

    @Test
    fun `every anchor is taught by some step`() {
        assertEquals(anchoredKeys, registeredKeys)
    }

    @Test
    fun `no step points at a missing anchor`() {
        assertEquals(emptySet<String>(), registeredKeys - anchoredKeys)
    }
}
