package com.xmu.course.data.tronclass.model

/** 不把 Retrofit、IO 或 JSON 异常直接泄漏到 UI 的结果封装。 */
sealed interface TronResult<out T> {
    data class Success<T>(val value: T) : TronResult<T>

    data class Error(val error: TronClassError) : TronResult<Nothing>
}

sealed interface TronClassError {
    data object NetworkError : TronClassError

    data object Unauthorized : TronClassError

    data object ResponseFormatChanged : TronClassError

    data class ParseError(val reason: ParseErrorReason) : TronClassError

    data object SessionMissing : TronClassError

    data object AuthRequired : TronClassError

    data object CurrentSemesterUnavailable : TronClassError

    data object CurrentSemesterAmbiguous : TronClassError

    data class ServerError(val statusCode: Int) : TronClassError

    data object StorageError : TronClassError
}

enum class ParseErrorReason {
    MissingCourses,
    MissingCourseId,
    InvalidCourseId,
    MissingCourseName,
    MissingAssignments,
    MissingAssignmentId,
    InvalidAssignmentId,
    MissingAssignmentTitle,
    InvalidAssignmentDeadline,
    MissingOfficialTodos,
    MissingOfficialTodoId,
    InvalidOfficialTodoId,
    MissingOfficialTodoTitle,
    MissingOfficialTodoCourseId,
    InvalidOfficialTodoDeadline,
    MissingSemesters,
    MissingSemesterId,
    InvalidSemesterId,
}
