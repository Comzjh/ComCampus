package com.xmu.course.data.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET

@JsonClass(generateAdapter = false)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    val draft: Boolean?,
    val prerelease: Boolean?,
    val assets: List<GitHubReleaseAssetDto>? = null,
)

@JsonClass(generateAdapter = false)
data class GitHubReleaseAssetDto(
    val name: String?,
    @Json(name = "browser_download_url") val browserDownloadUrl: String?,
    val digest: String?,
)

interface GitHubReleaseApiService {
    @GET("repos/Comzjh/ComCampus/releases/latest")
    suspend fun getLatestStableRelease(): Response<GitHubReleaseDto>
}
