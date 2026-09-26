package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.model.ParseErrorReason
import com.xmu.course.data.tronclass.model.TronClassError
import com.xmu.course.data.tronclass.model.TronResult

/** 仅接受唯一 active 学期；不使用顺序、最大 ID 或名称启发式。 */
class TronSemesterParser {
    fun resolve(response: TronSemestersResponseDto): TronResult<Long> {
        val semesters = response.semesters
            ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingSemesters))
        val active = semesters.filter { it.isActive == true }
        if (active.isEmpty()) return TronResult.Error(TronClassError.CurrentSemesterUnavailable)
        if (active.size > 1) return TronResult.Error(TronClassError.CurrentSemesterAmbiguous)
        val id = active.single().id
            ?: return TronResult.Error(TronClassError.ParseError(ParseErrorReason.MissingSemesterId))
        if (id <= 0L) return TronResult.Error(TronClassError.ParseError(ParseErrorReason.InvalidSemesterId))
        return TronResult.Success(id)
    }
}
