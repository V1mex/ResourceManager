package com.example.resourcemanager.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.logging.HttpLoggingInterceptor

class UpdateManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val owner = "V1mex"
    private val repo = "resourcemanager"

    private val service: GitHubService by lazy {
        // створюємо логінг-інтерцептор
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }

    suspend fun checkForUpdate(currentVersion: String) = withContext(ioDispatcher) {
        try {
            val release = service.getLatestRelease(owner, repo)
            val latestTag = release.tag_name

            if (isNewer(currentVersion, latestTag)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: throw IllegalStateException("No downloadable apk found")

                enqueueDownload(apkAsset.browser_download_url, apkAsset.name)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "Завантаження розпочалося...",
                        Toast.LENGTH_LONG
                    ).show()
                    }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Найактуальніша версія вже встановлена",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Помилка перевірки оновлень: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun isNewer(current: String, latest: String): Boolean {
        fun parse(v: String) = v.removePrefix("v")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
        val currParts = parse(current)
        val latestParts = parse(latest)
        for (i in 0 until maxOf(currParts.size, latestParts.size)) {
            val c = currParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun enqueueDownload(url: String, filename: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Завантаження оновлення")
            setDescription(filename)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        dm.enqueue(req)
    }
}
