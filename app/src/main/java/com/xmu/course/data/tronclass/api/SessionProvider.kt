package com.xmu.course.data.tronclass.api

import com.xmu.course.data.tronclass.auth.TronSession
import com.xmu.course.data.tronclass.auth.TronSessionStore

/** API 层读取认证状态的唯一入口。 */
fun interface SessionProvider {
    fun getSession(): TronSession?
}

class StoreSessionProvider(private val store: TronSessionStore) : SessionProvider {
    override fun getSession(): TronSession? = store.getSession()
}
