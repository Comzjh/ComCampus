package com.xmu.course.data.tronclass.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的加密存储实现。
 *
 * 使用应用私有文件保存 AES-GCM 密文，而不是多进程不安全的 SharedPreferences。
 * 密钥只存在 Android Keystore 中；这里不提供明文回退路径。
 */
internal class EncryptedTronSecureStorage(context: Context) : TronSecureStorage {
    private val applicationContext = context.applicationContext
    private val sessionFile = File(applicationContext.filesDir, SESSION_FILE_NAME)
    private val temporaryFile = File(applicationContext.filesDir, "$SESSION_FILE_NAME.tmp")

    override fun getString(key: String): String? {
        requireSupportedKey(key)
        if (!sessionFile.isFile) return null
        val payload = FileInputStream(sessionFile).use { it.readBytes() }
        if (payload.size < HEADER_SIZE) return null

        val buffer = ByteBuffer.wrap(payload)
        if (buffer.get() != FORMAT_VERSION) return null
        val ivLength = buffer.int
        if (ivLength <= 0 || ivLength > MAX_IV_SIZE || buffer.remaining() <= ivLength) return null
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override fun putString(key: String, value: String) {
        requireSupportedKey(key)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val output = ByteArrayOutputStream(HEADER_SIZE + iv.size + ciphertext.size)
        output.write(FORMAT_VERSION.toInt())
        output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(iv.size).array())
        output.write(iv)
        output.write(ciphertext)

        FileOutputStream(temporaryFile).use { stream ->
            stream.write(output.toByteArray())
            stream.fd.sync()
        }
        replaceSessionFile()
        // 清除 v0.7.0-alpha 早期实现留下的加密偏好文件，避免同一会话存在两份状态。
        applicationContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
    }

    override fun removeAll() {
        temporaryFile.delete()
        check(!sessionFile.exists() || sessionFile.delete()) {
            "encrypted session clear failed"
        }
        applicationContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
    }

    private fun requireSupportedKey(key: String) {
        check(key == PAYLOAD_KEY) { "unsupported secure storage key" }
    }

    private fun replaceSessionFile() {
        try {
            Files.move(
                temporaryFile.toPath(),
                sessionFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                sessionFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xmu_course_tronclass_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SESSION_FILE_NAME = "tronclass_session.enc"
        const val LEGACY_PREFS_NAME = "tronclass_secure_session"
        const val PAYLOAD_KEY = "session_payload"
        const val FORMAT_VERSION: Byte = 1
        const val TAG_BITS = 128
        const val MAX_IV_SIZE = 32
        const val HEADER_SIZE = 1 + Int.SIZE_BYTES
    }
}
