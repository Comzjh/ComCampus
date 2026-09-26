package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TronSemesterParserTest {
    private val parser = TronSemesterParser()

    @Test
    fun `唯一active学期返回其id`() {
        val result = parser.resolve(
            TronSemestersResponseDto(
                listOf(
                    TronSemesterDto(null, null, null, id = 1L, isActive = false),
                    TronSemesterDto(null, null, null, id = 2L, isActive = true),
                ),
            ),
        )

        assertEquals(TronResult.Success(2L), result)
    }

    @Test
    fun `没有active学期时fail safe`() {
        val result = parser.resolve(
            TronSemestersResponseDto(listOf(TronSemesterDto(null, null, null, id = 1L, isActive = false))),
        )

        assertEquals(TronClassError.CurrentSemesterUnavailable, (result as TronResult.Error).error)
    }

    @Test
    fun `多个active学期时fail safe`() {
        val result = parser.resolve(
            TronSemestersResponseDto(
                listOf(
                    TronSemesterDto(null, null, null, id = 1L, isActive = true),
                    TronSemesterDto(null, null, null, id = 2L, isActive = true),
                ),
            ),
        )

        assertEquals(TronClassError.CurrentSemesterAmbiguous, (result as TronResult.Error).error)
    }

    @Test
    fun `active学期缺少id时拒绝`() {
        val result = parser.resolve(
            TronSemestersResponseDto(listOf(TronSemesterDto(null, null, null, id = null, isActive = true))),
        )

        assertEquals(
            TronClassError.ParseError(com.xmu.course.data.tronclass.model.ParseErrorReason.MissingSemesterId),
            (result as TronResult.Error).error,
        )
    }
}
