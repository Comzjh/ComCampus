package com.xmu.course.data.tronclass.api

const val TRONCLASS_BASE_URL = "https://lnt.xmu.edu.cn/"

/** TronClass API 服务句柄；网络对象由 [TronClassApiFactory] 统一创建。 */
class TronClassApiClient(
    sessionProvider: SessionProvider,
    requestAuthenticator: RequestAuthenticator,
    baseUrl: String = TRONCLASS_BASE_URL,
) {
    val service: TronClassApiService = TronClassApiFactory.createService(
        sessionProvider = sessionProvider,
        requestAuthenticator = requestAuthenticator,
        baseUrl = baseUrl,
    )
}
