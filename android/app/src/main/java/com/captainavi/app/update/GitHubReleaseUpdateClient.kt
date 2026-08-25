package com.captainavi.app.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class AvailableAppUpdate(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String,
)

class GitHubReleaseUpdateClient(
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                followRedirects(true)
            }
        }
    }

    suspend fun fetchLatestRelease(): Result<AvailableAppUpdate> {
        return try {
            val response = client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "CaptainAvi-Android")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (!response.status.isSuccess()) {
                Result.failure(Exception("GitHub returned HTTP ${response.status.value}"))
            } else {
                val release = json.decodeFromString(GitHubReleaseDto.serializer(), response.bodyAsText())
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return Result.failure(Exception("Latest release has no APK asset"))
                val versionName = release.tagName.trim().removePrefix("v").removePrefix("V")
                Result.success(
                    AvailableAppUpdate(
                        tagName = release.tagName,
                        versionName = versionName,
                        releaseNotes = release.body.orEmpty().trim(),
                        apkDownloadUrl = apk.browserDownloadUrl,
                        apkFileName = apk.name,
                        apkSizeBytes = apk.size,
                        htmlUrl = release.htmlUrl,
                    ),
                )
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun downloadApk(url: String, destination: File, onProgress: (Float) -> Unit = {}): Result<File> {
        return try {
            destination.parentFile?.mkdirs()
            if (destination.exists()) destination.delete()
            onProgress(0.05f)
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, "CaptainAvi-Android")
                header(HttpHeaders.Accept, "application/octet-stream")
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Download failed HTTP ${response.status.value}"))
            }
            onProgress(0.2f)
            val bytes = response.readBytes()
            onProgress(0.9f)
            destination.writeBytes(bytes)
            onProgress(1f)
            Result.success(destination)
        } catch (error: Exception) {
            destination.delete()
            Result.failure(error)
        }
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String,
        val size: Long = 0L,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    companion object {
        const val DEFAULT_OWNER = "rileously"
        const val DEFAULT_REPO = "captainavi"
    }
}
