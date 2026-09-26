package com.xmu.course.data.tronclass.model

/** TronClass UI 需要的安全错误分类，不携带状态码、解析原因等数据层细节。 */
enum class TronClassUiErrorCategory {
    NetworkFailure,
    SessionExpired,
    ResponseFormatChanged,
    ParseFailure,
    AuthRequired,
    SemesterUnavailable,
    ServerFailure,
    StorageFailure,
}

data class TronClassUiError(val category: TronClassUiErrorCategory)

fun TronClassError.toUiError(): TronClassUiError = TronClassUiError(
    category = when (this) {
        TronClassError.NetworkError -> TronClassUiErrorCategory.NetworkFailure
        TronClassError.Unauthorized -> TronClassUiErrorCategory.SessionExpired
        TronClassError.ResponseFormatChanged -> TronClassUiErrorCategory.ResponseFormatChanged
        is TronClassError.ParseError -> TronClassUiErrorCategory.ParseFailure
        TronClassError.SessionMissing,
        TronClassError.AuthRequired,
        -> TronClassUiErrorCategory.AuthRequired
        TronClassError.CurrentSemesterUnavailable,
        TronClassError.CurrentSemesterAmbiguous,
        -> TronClassUiErrorCategory.SemesterUnavailable
        is TronClassError.ServerError -> TronClassUiErrorCategory.ServerFailure
        TronClassError.StorageError -> TronClassUiErrorCategory.StorageFailure
    },
)

fun TronClassUiError.toSafeMessage(): String = when (category) {
    TronClassUiErrorCategory.NetworkFailure -> "网络连接失败，请检查网络后重试"
    TronClassUiErrorCategory.SessionExpired -> "登录状态已失效，请重新登录"
    TronClassUiErrorCategory.ResponseFormatChanged -> "畅课返回的数据格式发生变化"
    TronClassUiErrorCategory.ParseFailure -> "课程数据解析失败，请稍后重试"
    TronClassUiErrorCategory.AuthRequired -> "请先登录厦大畅课"
    TronClassUiErrorCategory.SemesterUnavailable -> "无法确定当前畅课学期，请稍后重试"
    TronClassUiErrorCategory.ServerFailure -> "畅课服务暂时不可用，请稍后重试"
    TronClassUiErrorCategory.StorageFailure -> "本地数据保存失败，请重试"
}
