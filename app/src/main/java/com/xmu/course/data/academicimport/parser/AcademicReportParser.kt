package com.xmu.course.data.academicimport.parser

import com.xmu.course.contracts.academicimport.AcademicRecordKind
import com.xmu.course.data.academicimport.ExtractedAcademicDocument
import com.xmu.course.data.academicimport.ExtractedAcademicPage
import com.xmu.course.data.academicimport.ExtractedTextRun
import kotlin.math.abs

/**
 * 将 PDF 文本层转换为报告级候选记录。
 *
 * 先恢复厦大学业完成报告的“路径汇总行 + 课程明细块”，再兼容带明确标签的简单文本行。
 * 无法确认的行进入 NeedsReview，不把数字直接猜成学分，也不把候选课程直接送入 Grades。
 */
class AcademicReportParser(
    private val lineAssembler: AcademicReportLineAssembler = AcademicReportLineAssembler(),
) {
    fun parse(document: ExtractedAcademicDocument): AcademicImportParseResult {
        val lines = document.pages.flatMap { page ->
            lineAssembler.assemble(page)
        }
        if (lines.isEmpty()) return AcademicImportParseResult.Failed(AcademicParseFailureReason.NO_TEXT)

        val coordinateRows = parseCoordinateRows(document)
        val candidates = if (coordinateRows.isNotEmpty()) {
            coordinateRows
        } else {
            lines.mapNotNull { line -> parseLine(line) }
        }
        if (candidates.isEmpty()) {
            return AcademicImportParseResult.NeedsReview(
                records = emptyList(),
                reasons = listOf(AcademicReviewReason.NO_RELIABLE_COURSE_ROWS),
            )
        }

        val reasons = buildList {
            if (candidates.any { it.name == null }) add(AcademicReviewReason.COURSE_NAME_MISSING)
            if (candidates.any { it.creditText == null }) add(AcademicReviewReason.CREDIT_MISSING)
            if (candidates.any { it.kind == AcademicRecordKind.PLANNED }) {
                add(AcademicReviewReason.PLANNED_COURSE_PRESENT)
            }
            if (candidates.any { it.confidence == AcademicParseConfidence.LOW }) {
                add(AcademicReviewReason.AMBIGUOUS_COURSE_ROW)
            }
            if (candidates.any { it.provenance == AcademicImportCandidateProvenance.UNKNOWN_SECTION }) {
                add(AcademicReviewReason.UNCERTAIN_SOURCE)
            }
        }.distinct()

        return if (reasons.isEmpty()) {
            AcademicImportParseResult.Parsed(candidates)
        } else {
            AcademicImportParseResult.NeedsReview(candidates, reasons)
        }
    }

    /**
     * 厦大学业完成报告的课程表由课程路径汇总行和课程明细块组成。
     * 能唯一绑定汇总行时，用叶子路径和要求学分校正 PDF 重叠列；否则只使用明细块的
     * 坐标列，并把不充分的字段留给人工复核。
     */
    private fun parseCoordinateRows(document: ExtractedAcademicDocument): List<AcademicRecordCandidate> {
        val indexedLines = document.pages.flatMapIndexed { pageIndex, page ->
            lineAssembler.assemble(page).mapIndexed { lineIndex, line ->
                IndexedReportLine(pageIndex, lineIndex, line)
            }
        }
        val summaries = indexedLines.mapNotNull { indexed ->
            SUMMARY_ROW.matchEntire(indexed.line.text.normalizedReportText())?.let { match ->
                val path = match.groupValues[1].trim()
                val leafName = path.substringAfterLast('/').trim().takeIf(String::isNotBlank)
                val requiredCreditText = match.groupValues[2].trim().takeIf { it != "-" }
                val completedCredit = match.groupValues[3].toDoubleOrNull()
                if (leafName == null || completedCredit == null) {
                    null
                } else {
                    AcademicReportSummary(
                        indexed = indexed,
                        path = path,
                        leafName = leafName,
                        requiredCreditText = requiredCreditText,
                        completedCredit = completedCredit,
                    )
                }
            }
        }
        if (summaries.isEmpty()) return emptyList()

        val detailStarts = document.pages.flatMapIndexed { pageIndex, page ->
            page.textRuns
                .filter { it.x in DETAIL_STATUS_COLUMN_X && it.text.trim() == "否" }
                .sortedBy { it.y }
                .map { LocatedDetailStart(pageIndex, LocatedTextRun(it, page.pageNumber)) }
        }
        if (detailStarts.isEmpty()) return emptyList()

        val linkedSummaries = summaries.mapNotNull { summary ->
            val nextSummary = summaries.firstOrNull { it.indexed.isAfter(summary.indexed) }
            val detailMarkers = runsBetween(
                document = document,
                start = summary.indexed,
                end = nextSummary?.indexed,
            ).filter { it.x in DETAIL_STATUS_COLUMN_X && it.text.trim() == "否" }
            if (detailMarkers.size == 1 && summary.path.count { it == '/' } >= MIN_COURSE_PATH_SEPARATORS) {
                summary to detailMarkers.single()
            } else {
                null
            }
        }

        return detailStarts.mapIndexed { detailIndex, detailStart ->
            val nextDetailStart = detailStarts.getOrNull(detailIndex + 1)
            val detailRuns = runsBetween(
                document = document,
                start = detailStart,
                end = nextDetailStart,
            )
            val linkedSummary = linkedSummaries.firstOrNull { (_, marker) ->
                marker.pageNumber == detailStart.run.pageNumber &&
                    abs(marker.y - detailStart.run.y) <= LINE_Y_EPSILON
            }?.first
            val rawName = extractCourseName(detailRuns)
            val rawCredit = extractCredit(detailRuns, detailStart.run)
            val courseName = linkedSummary?.leafName ?: rawName
            val creditText = linkedSummary?.requiredCreditText ?: rawCredit
            val completed = linkedSummary?.completedCredit?.let { it > 0.0 } ?: hasSemester(detailRuns)
            val kind = if (completed) AcademicRecordKind.COMPLETED else AcademicRecordKind.UNKNOWN
            val reliableName = courseName != null && courseName.length >= 2
            val confidence = when {
                creditText != null && completed && reliableName ->
                    AcademicParseConfidence.HIGH
                courseName != null || creditText != null -> AcademicParseConfidence.MEDIUM
                else -> AcademicParseConfidence.LOW
            }
            AcademicRecordCandidate(
                name = courseName,
                creditText = creditText,
                category = linkedSummary?.path?.substringBeforeLast('/')?.trim()?.takeIf(String::isNotBlank),
                kind = kind,
                confidence = confidence,
                pageNumber = detailStart.run.pageNumber,
                provenance = linkedSummary?.let { AcademicImportCandidateProvenance.COMPLETED_DETAIL }
                    ?: AcademicImportCandidateProvenance.UNKNOWN_SECTION,
            )
        }
    }

    private fun extractCourseName(runs: List<LocatedTextRun>): String? {
        val raw = runs
            .asSequence()
            .filter { it.x in COURSE_NAME_COLUMN_X }
            .sortedWith(compareBy<LocatedTextRun> { it.y }.thenBy { it.x })
            .joinToString(separator = "") { it.text }
            .replace(Regex("\\s+"), "")
            .trim()
        val firstNameChar = raw.indexOfFirst { it.isLetter() }
        return if (firstNameChar >= 0) raw.substring(firstNameChar).takeIf(String::isNotBlank) else null
    }

    private fun extractCredit(runs: List<LocatedTextRun>, start: LocatedTextRun): String? {
        return runs
            .asSequence()
            .filter { it.x in CREDIT_COLUMN_X && abs(it.y - start.y) <= maxOf(2f, start.height) }
            .sortedBy { it.x }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "")
            .takeIf { it.matches(CREDIT) }
    }

    private fun hasSemester(runs: List<LocatedTextRun>): Boolean {
        val semester = runs
            .asSequence()
            .filter { it.x in SEMESTER_COLUMN_X }
            .sortedWith(compareBy<LocatedTextRun> { it.y }.thenBy { it.x })
            .joinToString(separator = "") { it.text.trim() }
        return SEMESTER_MARKER.containsMatchIn(semester)
    }

    private fun runsBetween(
        document: ExtractedAcademicDocument,
        start: IndexedReportLine,
        end: IndexedReportLine?,
    ): List<LocatedTextRun> {
        return runsBetween(
            document = document,
            startPageIndex = start.pageIndex,
            startY = start.line.y,
            endPageIndex = end?.pageIndex,
            endY = end?.line?.y,
        )
    }

    private fun runsBetween(
        document: ExtractedAcademicDocument,
        start: LocatedDetailStart,
        end: LocatedDetailStart?,
    ): List<LocatedTextRun> {
        return runsBetween(
            document = document,
            startPageIndex = start.pageIndex,
            startY = start.run.y,
            endPageIndex = end?.pageIndex,
            endY = end?.run?.y,
            includeStart = true,
        )
    }

    private fun runsBetween(
        document: ExtractedAcademicDocument,
        startPageIndex: Int,
        startY: Float,
        endPageIndex: Int?,
        endY: Float?,
        includeStart: Boolean = false,
    ): List<LocatedTextRun> {
        val lastPageIndex = endPageIndex ?: document.pages.lastIndex
        return buildList {
            for (pageIndex in startPageIndex..lastPageIndex) {
                val page = document.pages[pageIndex]
                val upperBound = if (endPageIndex != null && pageIndex == endPageIndex) endY!! else Float.POSITIVE_INFINITY
                page.textRuns
                    .asSequence()
                    .filter {
                        val afterStart = if (pageIndex != startPageIndex) {
                            true
                        } else if (includeStart) {
                            it.y >= startY - LINE_Y_EPSILON
                        } else {
                            it.y > startY + LINE_Y_EPSILON
                        }
                        afterStart && it.y < upperBound - LINE_Y_EPSILON
                    }
                    .forEach { add(LocatedTextRun(it, page.pageNumber)) }
            }
        }
    }

    private fun parseLine(line: AcademicReportLine): AcademicRecordCandidate? {
        val normalized = line.text.replace(Regex("\\s+"), " ").trim()
        val match = EXPLICIT_COURSE_ROW.matchEntire(normalized)
        val nameOnlyMatch = COURSE_WITHOUT_CREDIT.matchEntire(normalized)
        if (match == null && nameOnlyMatch == null) return null
        val name = (match?.groups?.get(1) ?: nameOnlyMatch?.groups?.get(1))
            ?.value?.trim()?.takeIf(String::isNotBlank)
        val creditText = match?.groups?.get(2)?.value?.trim()?.takeIf(String::isNotBlank)
        val kind = when {
            PLANNED_MARKERS.containsMatchIn(normalized) -> AcademicRecordKind.PLANNED
            COMPLETED_MARKERS.containsMatchIn(normalized) -> AcademicRecordKind.COMPLETED
            else -> AcademicRecordKind.UNKNOWN
        }
        val confidence = when {
            name != null && creditText != null && kind == AcademicRecordKind.COMPLETED ->
                AcademicParseConfidence.HIGH
            name != null && creditText != null -> AcademicParseConfidence.MEDIUM
            else -> AcademicParseConfidence.LOW
        }
        return AcademicRecordCandidate(
            name = name,
            creditText = creditText,
            kind = kind,
            confidence = confidence,
            pageNumber = line.pageNumber,
            provenance = when (kind) {
                AcademicRecordKind.COMPLETED -> AcademicImportCandidateProvenance.COMPLETED_DETAIL
                AcademicRecordKind.PLANNED -> AcademicImportCandidateProvenance.PROGRAM_REQUIREMENT
                AcademicRecordKind.UNKNOWN -> AcademicImportCandidateProvenance.UNKNOWN_SECTION
            },
        )
    }

    private companion object {
        val SUMMARY_ROW = Regex(
            """^(.*?)\s+要求学分\s*[:：]\s*([0-9]+(?:\.[0-9]+)?|-)\s*\|\s*已完成学分\s*[:：]\s*([0-9]+(?:\.[0-9]+)?)\s*\|\s*本学期已选学分\s*[:：]\s*([0-9]+(?:\.[0-9]+)?)\s*.*$""",
        )
        val DETAIL_STATUS_COLUMN_X = 20f..45f
        val COURSE_NAME_COLUMN_X = 68.5f..70.5f
        val CREDIT_COLUMN_X = 80.5f..82.5f
        val SEMESTER_COLUMN_X = 44f..56f
        val CREDIT = Regex("""[0-9]+(?:\.[0-9]+)?""")
        // 坐标列中的年份字符可能与邻列交错，但已修课程仍会留下可识别的年份前缀。
        val SEMESTER_MARKER = Regex("20[0-9]{2}")
        const val MIN_COURSE_PATH_SEPARATORS = 2
        const val LINE_Y_EPSILON = 0.5f
        val EXPLICIT_COURSE_ROW = Regex(
            """^.*?(?:课程名称|课程)\s*[:：]\s*(.*?)\s+(?:学分|学分数)\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)\s*$""",
        )
        val COURSE_WITHOUT_CREDIT = Regex(
            """^.*?(?:课程名称|课程)\s*[:：]\s*(.*?)\s*$""",
        )
        val COMPLETED_MARKERS = Regex("已修|已完成|已获得|已获")
        val PLANNED_MARKERS = Regex("培养方案|候选|计划|待修")
    }
}

private data class IndexedReportLine(
    val pageIndex: Int,
    val lineIndex: Int,
    val line: AcademicReportLine,
)

private data class AcademicReportSummary(
    val indexed: IndexedReportLine,
    val path: String,
    val leafName: String,
    val requiredCreditText: String?,
    val completedCredit: Double,
)

private data class LocatedTextRun(
    val run: ExtractedTextRun,
    val pageNumber: Int,
) {
    val text: String get() = run.text
    val x: Float get() = run.x
    val y: Float get() = run.y
    val height: Float get() = run.height
}

private data class LocatedDetailStart(
    val pageIndex: Int,
    val run: LocatedTextRun,
)

private fun IndexedReportLine.isAfter(other: IndexedReportLine): Boolean {
    return pageIndex > other.pageIndex || (pageIndex == other.pageIndex && lineIndex > other.lineIndex)
}

private fun String.normalizedReportText(): String = replace(Regex("\\s+"), " ").trim()

data class AcademicReportLine(
    val pageNumber: Int,
    val text: String,
    val y: Float,
)

/** 以坐标重建同一页的横向文本行，不改变字符语义。 */
class AcademicReportLineAssembler(
    private val yTolerance: Float = 2.5f,
) {
    fun assemble(page: ExtractedAcademicPage): List<AcademicReportLine> {
        val groups = mutableListOf<MutableList<ExtractedTextRun>>()
        page.textRuns.sortedWith(compareBy<ExtractedTextRun> { it.y }.thenBy { it.x })
            .forEach { run ->
                val group = groups.lastOrNull { abs(it.first().y - run.y) <= yTolerance }
                if (group == null) groups += mutableListOf(run) else group += run
            }

        return groups.map { runs ->
            val ordered = runs.sortedBy { it.x }
            AcademicReportLine(
                pageNumber = page.pageNumber,
                text = joinRuns(ordered),
                y = ordered.map { it.y }.average().toFloat(),
            )
        }.sortedBy { it.y }
    }

    private fun joinRuns(runs: List<ExtractedTextRun>): String {
        if (runs.isEmpty()) return ""
        val result = StringBuilder(runs.first().text)
        runs.zipWithNext().forEach { (previous, current) ->
            val gap = current.x - (previous.x + previous.width)
            if (gap > maxOf(2f, previous.height * 0.35f)) result.append(' ')
            result.append(current.text)
        }
        return result.toString()
    }
}
