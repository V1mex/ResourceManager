package com.example.resourcemanager.update

import retrofit2.http.GET
import retrofit2.http.Path

data class ReleaseResponse(
    val tag_name: String,
    val assets: List<Asset>
)

data class Asset(
    val name: String,
    val browser_download_url: String
)

interface GitHubService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): ReleaseResponse
}
