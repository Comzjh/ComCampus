package com.xmu.course.data.tronclass.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TronSemesterSorterTest {
    @Test
    fun `学年优先且同学年按学期排序`() {
        assertEquals(
            listOf("2026-2027-1", "2025-2026-2", "2025-2026-1"),
            sortTronSemesterLabels(listOf("2025-2026-1", "2026-2027-1", "2025-2026-2")),
        )
    }

    @Test
    fun `短格式和季节格式可排序`() {
        assertEquals(
            listOf("2026-2027秋季学期", "2026-1", "2025-1"),
            sortTronSemesterLabels(listOf("2025-1", "2026-1", "2026-2027秋季学期")),
        )
    }

    @Test
    fun `未知和空标签不会崩溃且结果稳定`() {
        assertEquals(
            listOf("2026-2027-1", "???", "未标注学期"),
            sortTronSemesterLabels(listOf("未标注学期", "???", "2026-2027-1", " ")),
        )
    }
}
