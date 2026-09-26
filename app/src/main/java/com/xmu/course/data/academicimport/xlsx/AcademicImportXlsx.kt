package com.xmu.course.data.academicimport.xlsx

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Academic Import 的临时 Excel 数据源行。
 *
 * 所有字段都保留转换阶段的原始文本；缺失值保持 null，禁止在转换层猜测课程或学分。
 */
data class AcademicXlsxRow(
    val courseName: String?,
    val creditText: String?,
    val semester: String? = null,
    val category: String? = null,
    val status: String,
    val sourcePage: Int? = null,
    val confidence: String,
    val reviewReason: String? = null,
)

data class AcademicXlsxDocument(val rows: List<AcademicXlsxRow>)

/** 只写入一个 worksheet 的最小 OOXML 文件，不引入重量级表格依赖。 */
class AcademicXlsxWriter {
    fun write(rows: List<AcademicXlsxRow>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            addEntry(zip, "[Content_Types].xml", CONTENT_TYPES)
            addEntry(zip, "_rels/.rels", ROOT_RELS)
            addEntry(zip, "xl/workbook.xml", WORKBOOK)
            addEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            addEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows))
        }
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sheetXml(rows: List<AcademicXlsxRow>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetData>")
        append(rowXml(1, HEADERS.toList()))
        rows.forEachIndexed { index, row ->
            append(rowXml(index + 2, listOf(
                row.courseName,
                row.creditText,
                row.semester,
                row.category,
                row.status,
                row.sourcePage?.toString(),
                row.confidence,
                row.reviewReason,
            )))
        }
        append("</sheetData></worksheet>")
    }

    private fun rowXml(rowNumber: Int, values: List<String?>): String = buildString {
        append("<row r=\"$rowNumber\">")
        values.forEachIndexed { index, value ->
            if (value != null) {
                val reference = "${columnName(index)}$rowNumber"
                append("<c r=\"$reference\" t=\"inlineStr\"><is><t")
                if (value.startsWith(" ") || value.endsWith(" ") || value.contains("\n")) {
                    append(" xml:space=\"preserve\"")
                }
                append(">")
                append(xmlEscape(value))
                append("</t></is></c>")
            }
        }
        append("</row>")
    }

    private fun columnName(index: Int): String = when {
        index < 26 -> ('A'.code + index).toChar().toString()
        else -> "${('A'.code + index / 26 - 1).toChar()}${('A'.code + index % 26).toChar()}"
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        val HEADERS = arrayOf("课程名称", "课程学分", "学期", "类别", "状态", "来源页", "置信度", "待确认原因")
        val CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent()
        val ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        val WORKBOOK = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="AcademicImport" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val WORKBOOK_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
    }
}

/** 读取本模块生成的单 worksheet Excel 数据源。 */
class AcademicXlsxReader {
    fun read(input: InputStream): AcademicXlsxDocument {
        val sheetBytes = ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null && entry!!.name != "xl/worksheets/sheet1.xml") {
                zip.closeEntry()
                entry = zip.nextEntry
            }
            require(entry != null) { "Excel 数据源缺少 worksheet" }
            zip.readBytes()
        }
        val rows = readRows(sheetBytes)
        require(rows.isNotEmpty()) { "Excel 数据源缺少表头" }
        val header = rows.first()
        val headerValues = (0 until HEADERS.size).map { header[it].orEmpty() }
        require(headerValues == HEADERS)
        return AcademicXlsxDocument(rows.drop(1).mapNotNull(::toRow))
    }

    private fun readRows(sheetBytes: ByteArray): List<Map<Int, String>> {
        val rows = mutableListOf<Map<Int, String>>()
        val parserFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        parserFactory.newSAXParser().parse(sheetBytes.inputStream(), object : DefaultHandler() {
            var currentCells: MutableMap<Int, String>? = null
            var currentColumn: Int? = null
            var readingText = false
            val text = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (localName.orEmpty().ifBlank { qName.orEmpty().substringAfter(':') }) {
                    "row" -> currentCells = linkedMapOf()
                    "c" -> currentColumn = attributes?.getValue("r")
                        ?.takeWhile(Char::isLetter)
                        ?.columnIndex()
                    "t" -> {
                        text.clear()
                        readingText = true
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (readingText) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName.orEmpty().ifBlank { qName.orEmpty().substringAfter(':') }) {
                    "t" -> {
                        val column = currentColumn
                        if (column != null) currentCells?.set(column, text.toString())
                        readingText = false
                    }
                    "row" -> {
                        currentCells?.let(rows::add)
                        currentCells = null
                        currentColumn = null
                    }
                }
            }
        })
        return rows
    }

    private fun String.columnIndex(): Int = fold(0) { value, char ->
        value * 26 + (char.uppercaseChar().code - 'A'.code + 1)
    } - 1

    private fun toRow(cells: Map<Int, String>): AcademicXlsxRow? {
        if (cells.values.all(String::isBlank)) return null
        return AcademicXlsxRow(
            courseName = cells[0]?.takeIf(String::isNotBlank),
            creditText = cells[1]?.takeIf(String::isNotBlank),
            semester = cells[2]?.takeIf(String::isNotBlank),
            category = cells[3]?.takeIf(String::isNotBlank),
            status = cells[4].orEmpty(),
            sourcePage = cells[5]?.toIntOrNull(),
            confidence = cells[6].orEmpty(),
            reviewReason = cells[7]?.takeIf(String::isNotBlank),
        )
    }

    private companion object {
        const val SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val HEADERS = listOf("课程名称", "课程学分", "学期", "类别", "状态", "来源页", "置信度", "待确认原因")
    }
}

/** 临时 Excel 数据源：覆盖旧文件，不保存原始 PDF。 */
class TemporaryAcademicXlsxStore(
    private val file: File,
    private val writer: AcademicXlsxWriter = AcademicXlsxWriter(),
    private val reader: AcademicXlsxReader = AcademicXlsxReader(),
) {
    fun writeAndRead(rows: List<AcademicXlsxRow>): AcademicXlsxDocument {
        file.parentFile?.mkdirs()
        file.outputStream().use { writer.write(rows, it) }
        return file.inputStream().use(reader::read)
    }

    /** 读取最近一次临时 Excel；不存在时返回 null，不触碰原始 PDF。 */
    fun readIfExists(): AcademicXlsxDocument? {
        if (!file.isFile || file.length() == 0L) return null
        return file.inputStream().use(reader::read)
    }
}
