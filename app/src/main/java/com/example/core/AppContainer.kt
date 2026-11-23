package com.example.core

import android.content.Context
import com.example.data.bridge.MseBridgeClient
import com.example.data.fonts.FontCatalogRepository
import com.example.data.mse.MseTemplateCatalogRepository
import com.example.data.mse.MseWorkspaceRepository
import com.example.data.nexus.NexusHubBridgeService
import com.example.data.nexus.NexusHubRepository
import com.example.data.settings.SettingsRepository
import com.example.data.upstream.GitHubUpdateWatcher
import com.example.data.upstream.UpstreamManifestRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Clean constructor-injected dependency container for TCG Studio.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val moshi: Moshi = Moshi.Builder().build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
        )
        .build()

    val settingsRepository = SettingsRepository(appContext)

    val mseBridgeClient = MseBridgeClient(
        okHttpClient = okHttpClient,
        moshi = moshi,
        settingsRepository = settingsRepository,
    )

    val mseWorkspaceRepository = MseWorkspaceRepository(
        context = appContext,
        moshi = moshi,
    )

    val mseTemplateCatalogRepository = MseTemplateCatalogRepository(
        context = appContext,
        moshi = moshi,
        okHttpClient = okHttpClient,
    )

    val fontCatalogRepository = FontCatalogRepository(
        context = appContext,
        moshi = moshi,
        okHttpClient = okHttpClient,
        bridgeClient = mseBridgeClient,
        settingsRepository = settingsRepository,
    )

    val upstreamManifestRepository = UpstreamManifestRepository(
        context = appContext,
        moshi = moshi,
    )

    val gitHubUpdateWatcher = GitHubUpdateWatcher(
        okHttpClient = okHttpClient,
        moshi = moshi,
    )

    val nexusHubBridgeService = NexusHubBridgeService(
        githubOkHttpClient = okHttpClient,
        moshi = moshi,
    )

    val nexusHubRepository = NexusHubRepository(
        context = appContext,
        moshi = moshi,
    )
}
