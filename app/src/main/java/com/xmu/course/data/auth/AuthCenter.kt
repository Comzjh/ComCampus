package com.xmu.course.data.auth

import android.content.Context
import com.xmu.course.data.tronclass.auth.TronSessionStore

enum class AuthService {
    WISEDU,
    TRONCLASS,
}

enum class AuthStatus {
    CHECKING,
    AUTHENTICATED,
    AUTH_REQUIRED,
    EXPIRED,
    UNKNOWN,
}

data class AuthServiceState(
    val service: AuthService,
    val status: AuthStatus,
)

interface AuthStatusSource {
    suspend fun check(): AuthStatus
}

interface WiseduAuthStatusController : AuthStatusSource {
    fun applyObservation(observation: WiseduAuthObservation): AuthStatus

    fun clear()
}

/** 只记录“成功完成过一次课表导入”的历史提示，不保存账号、密码或 Cookie。 */
interface WiseduSessionMarker {
    fun isVerified(): Boolean

    fun markVerified()

    fun clear()
}

class SharedPreferencesWiseduSessionMarker(context: Context) : WiseduSessionMarker {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun isVerified(): Boolean = preferences.getBoolean(KEY_VERIFIED, false)

    override fun markVerified() {
        preferences.edit().putBoolean(KEY_VERIFIED, true).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_VERIFIED).apply()
    }

    private companion object {
        const val PREFERENCES = "wisedu_auth_status"
        const val KEY_VERIFIED = "session_verified"
    }
}

class WiseduAuthStatusSource(
    private val marker: WiseduSessionMarker,
    private val verifier: WiseduAuthVerifier = ConservativeWiseduAuthVerifier,
) : WiseduAuthStatusController {
    @Volatile
    private var runtimeStatus: AuthStatus = AuthStatus.UNKNOWN

    override suspend fun check(): AuthStatus {
        // 读取历史提示仅用于兼容旧数据；它不能改变当前状态。
        marker.isVerified()
        return runtimeStatus
    }

    override fun applyObservation(observation: WiseduAuthObservation): AuthStatus {
        return verifier.verify(observation).also { runtimeStatus = it }
    }

    override fun clear() {
        runtimeStatus = AuthStatus.UNKNOWN
        marker.clear()
    }
}

class TronClassAuthStatusSource(
    private val sessionStore: TronSessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AuthStatusSource {
    override suspend fun check(): AuthStatus {
        val session = runCatching { sessionStore.getSession() }.getOrNull()
            ?: return AuthStatus.AUTH_REQUIRED
        return if (session.isExpired(now())) AuthStatus.EXPIRED else AuthStatus.AUTHENTICATED
    }
}
