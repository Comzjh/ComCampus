package com.xmu.course.data.update

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed interface UpdateCheckResult {
    data object NotChecked : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Unknown : UpdateCheckResult
    data object Failed : UpdateCheckResult
    data object Throttled : UpdateCheckResult
    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val apkUrl: String? = null,
        val apkDigest: String? = null,
        val apkName: String? = null,
    ) : UpdateCheckResult
}

interface UpdateSettings {
    fun isAutomaticCheckEnabled(): Boolean
    fun setAutomaticCheckEnabled(enabled: Boolean)
    fun lastAutomaticCheckAt(): Long
    fun setLastAutomaticCheckAt(timestamp: Long)
}

class SharedPreferencesUpdateSettings(context: android.content.Context) : UpdateSettings {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE)

    override fun isAutomaticCheckEnabled(): Boolean = try {
        preferences.getBoolean(KEY_AUTOMATIC, true)
    } catch (_: ClassCastException) {
        false
    }

    override fun setAutomaticCheckEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATIC, enabled).apply()
    }

    override fun lastAutomaticCheckAt(): Long = try {
        preferences.getLong(KEY_LAST_CHECK, 0L)
    } catch (_: ClassCastException) {
        System.currentTimeMillis()
    }

    override fun setLastAutomaticCheckAt(timestamp: Long) {
        preferences.edit().putLong(KEY_LAST_CHECK, timestamp).apply()
    }

    private companion object {
        const val PREFERENCES = "update_preferences"
        const val KEY_AUTOMATIC = "automatic_check_enabled"
        const val KEY_LAST_CHECK = "last_automatic_check_at"
    }
}

interface GitHubUpdateRepositoryContract {
    suspend fun check(currentVersion: String, manual: Boolean): UpdateCheckResult
    suspend fun download(update: UpdateCheckResult.Available): File
}

class GitHubUpdateRepository(
    private val api: GitHubReleaseApiService,
    private val settings: UpdateSettings,
    private val clock: () -> Long = System::currentTimeMillis,
    private val throttleMillis: Long = TimeUnit.HOURS.toMillis(24),
    private val appContext: android.content.Context? = null,
    private val downloadClient: OkHttpClient? = null,
) : GitHubUpdateRepositoryContract {
    override suspend fun check(currentVersion: String, manual: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!manual && !settings.isAutomaticCheckEnabled()) return@withContext UpdateCheckResult.NotChecked
        if (!manual && clock() - settings.lastAutomaticCheckAt() < throttleMillis) {
            return@withContext UpdateCheckResult.Throttled
        }
        if (!manual) settings.setLastAutomaticCheckAt(clock())

        val response = try {
            api.getLatestStableRelease()
        } catch (_: IOException) {
            return@withContext UpdateCheckResult.Failed
        } catch (_: Exception) {
            return@withContext UpdateCheckResult.Failed
        }
        if (!response.isSuccessful) return@withContext UpdateCheckResult.Failed
        val release = response.body() ?: return@withContext UpdateCheckResult.Unknown
        if (release.draft == true || release.prerelease == true) return@withContext UpdateCheckResult.Unknown
        val latest = release.tagName ?: return@withContext UpdateCheckResult.Unknown
        val url = release.htmlUrl ?: return@withContext UpdateCheckResult.Unknown
        val apk = release.assets.orEmpty().firstOrNull { asset ->
            asset.name?.endsWith(".apk", ignoreCase = true) == true &&
                asset.browserDownloadUrl?.let(::isTrustedReleaseAssetUrl) == true
        }
        when {
            VersionComparator.isNewer(currentVersion, latest) -> UpdateCheckResult.Available(
                currentVersion = currentVersion,
                latestVersion = latest,
                releaseUrl = url,
                apkUrl = apk?.browserDownloadUrl,
                apkDigest = apk?.digest,
                apkName = apk?.name,
            )
            VersionComparator.compare(currentVersion, latest) == null -> UpdateCheckResult.Unknown
            else -> UpdateCheckResult.UpToDate
        }
    }

    override suspend fun download(update: UpdateCheckResult.Available): File = withContext(Dispatchers.IO) {
        val context = checkNotNull(appContext) { "Update download context is unavailable" }
        val client = checkNotNull(downloadClient) { "Update download client is unavailable" }
        val url = checkNotNull(update.apkUrl) { "The release does not contain a trusted APK asset" }
        check(isTrustedReleaseAssetUrl(url)) { "Untrusted update download URL" }
        val expectedDigest = update.apkDigest
            ?.removePrefix("sha256:")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { SHA256_PATTERN.matches(it) }
            ?: throw IOException("The release APK has no valid SHA-256 digest")

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        response.use { result ->
            if (!result.isSuccessful) throw IOException("Update download failed with HTTP ${result.code()}")
            val body = result.body() ?: throw IOException("Update download returned an empty body")
            if (body.contentLength() > MAX_APK_BYTES) throw IOException("Update APK is unexpectedly large")

            val directory = File(context.cacheDir, "updates")
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot prepare update cache")
            val temporaryFile = File.createTempFile("comcampus-update-", ".part", directory)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                body.byteStream().use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_APK_BYTES) throw IOException("Update APK is unexpectedly large")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val actualDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!actualDigest.equals(expectedDigest, ignoreCase = true)) {
                    throw IOException("Update APK digest verification failed")
                }
                val fileName = "ComCampus-${safeVersionForFilename(update.latestVersion)}.apk"
                val apkFile = File(directory, fileName)
                if (apkFile.exists() && !apkFile.delete()) throw IOException("Cannot replace cached update APK")
                if (!temporaryFile.renameTo(apkFile)) throw IOException("Cannot finalize downloaded update APK")
                apkFile
            } catch (error: Exception) {
                temporaryFile.delete()
                throw error
            }
        }
    }

    private fun isTrustedReleaseAssetUrl(url: String): Boolean =
        url.startsWith("https://github.com/Comzjh/ComCampus/releases/download/")

    private fun safeVersionForFilename(version: String): String =
        version.removePrefix("v").filter { it.isLetterOrDigit() || it == '.' || it == '-' }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        const val MAX_APK_BYTES = 100L * 1024L * 1024L
    }
}

object GitHubUpdateRepositoryFactory {
    fun create(
        context: android.content.Context,
        settings: UpdateSettings = SharedPreferencesUpdateSettings(context),
    ): GitHubUpdateRepositoryContract {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(com.xmu.course.AppLinks.GITHUB_API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleaseApiService::class.java)
        val downloadClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
        return GitHubUpdateRepository(
            api = api,
            settings = settings,
            appContext = context.applicationContext,
            downloadClient = downloadClient,
        )
    }
}
