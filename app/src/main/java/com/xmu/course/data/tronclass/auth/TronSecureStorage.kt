package com.xmu.course.data.tronclass.auth

/** 可替换的安全持久化边界，业务会话代码不绑定具体 Android 存储实现。 */
internal interface TronSecureStorage {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun removeAll()
}
