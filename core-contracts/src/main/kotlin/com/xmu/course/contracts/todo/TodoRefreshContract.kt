package com.xmu.course.contracts.todo

/** Todo Feature 需要的外部刷新结果；不暴露 HTTP、Provider、认证或 DTO 细节。 */
sealed interface TodoRefreshResult {
    data class Success(val importedCount: Int) : TodoRefreshResult
    data object SessionExpired : TodoRefreshResult
    data object Error : TodoRefreshResult
}

/** Todo Feature 的刷新能力边界；实现可以来自本地或任意外部来源。 */
fun interface TodoRefreshReader {
    suspend fun refresh(): TodoRefreshResult
}
