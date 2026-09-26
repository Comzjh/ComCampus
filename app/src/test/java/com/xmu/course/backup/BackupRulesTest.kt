package com.xmu.course.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 静态验证备份边界：allowBackup=false + 显式 Android 12+ dataExtractionRules，
 * 且规则对云备份与设备转移均为 deny-all，覆盖会话/认证/Room/学术快照存储域。
 */
class BackupRulesTest {
    private val moduleDir = File(System.getProperty("user.dir")!!).takeIf { it.endsWith("app") } ?: File("app")

    private fun manifest() = File(moduleDir, "src/main/AndroidManifest.xml").readText()

    private fun rulesDoc() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(File(moduleDir, "src/main/res/xml/data_extraction_rules.xml"))

    @Test fun `manifest disables backup and points to explicit extraction rules`() {
        val m = manifest()
        assertTrue("allowBackup must stay false", m.contains("android:allowBackup=\"false\""))
        assertTrue(
            "manifest must reference dataExtractionRules",
            m.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
        assertTrue(
            "manifest must reference legacy fullBackupContent for API < 31",
            m.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
    }

    @Test fun `legacy rules deny backup for all storage domains`() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(moduleDir, "src/main/res/xml/backup_rules.xml"))
        val root = doc.getElementsByTagName("full-backup-content").item(0)
        val excluded = (0 until root.childNodes.length)
            .map { root.childNodes.item(it) }
            .filter { it.nodeName == "exclude" }
            .map { it.attributes.getNamedItem("domain").nodeValue }
        assertEquals(setOf("root", "file", "database", "sharedpref", "external"), excluded.toSet())
    }

    @Test fun `extraction rules deny cloud backup for all storage domains`() {
        val doc = rulesDoc()
        val cloud = doc.getElementsByTagName("cloud-backup").item(0)
        val excluded = (0 until cloud.childNodes.length)
            .map { cloud.childNodes.item(it) }
            .filter { it.nodeName == "exclude" }
            .map { it.attributes.getNamedItem("domain").nodeValue to it.attributes.getNamedItem("path").nodeValue }
        assertEquals(
            setOf("root", "file", "database", "sharedpref", "external"),
            excluded.map { it.first }.toSet(),
        )
        excluded.forEach { (domain, path) -> assertEquals("domain $domain must be fully excluded", ".", path) }
    }

    @Test fun `extraction rules deny device transfer for all storage domains`() {
        val doc = rulesDoc()
        val transfer = doc.getElementsByTagName("device-transfer").item(0)
        val excluded = (0 until transfer.childNodes.length)
            .map { transfer.childNodes.item(it) }
            .filter { it.nodeName == "exclude" }
            .map { it.attributes.getNamedItem("domain").nodeValue }
        assertEquals(setOf("root", "file", "database", "sharedpref", "external"), excluded.toSet())
    }

    @Test fun `rules file declares no include elements`() {
        val doc = rulesDoc()
        assertFalse(doc.getElementsByTagName("include").length > 0)
    }
}
