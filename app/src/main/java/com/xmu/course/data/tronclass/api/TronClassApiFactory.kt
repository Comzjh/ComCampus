package com.xmu.course.data.tronclass.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** TronClass 网络对象的唯一创建边界；不改变请求、认证或超时策略。 */
object TronClassApiFactory {
    fun createService(
        sessionProvider: SessionProvider,
        requestAuthenticator: RequestAuthenticator,
        baseUrl: String = TRONCLASS_BASE_URL,
    ): TronClassApiService {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionProvider, requestAuthenticator))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .callFactory(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TronClassApiService::class.java)
    }
}
