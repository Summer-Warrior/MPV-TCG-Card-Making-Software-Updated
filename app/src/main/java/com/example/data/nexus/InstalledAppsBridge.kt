package com.example.data.nexus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.domain.nexus.InstalledCompanionAppInfo

/**
 * Private Play Store Companion App Bridge for detecting and launching the locally installed
 * NexusTCG Client (`com.aistudio.nexustcg.vxqrmk`) using manifest-declared `<queries>`
 * (privacy-friendly alternative to QUERY_ALL_PACKAGES).
 */
class InstalledAppsBridge(
    private val context: Context,
) {
    companion object {
        const val NEXUS_TCG_PACKAGE = "com.aistudio.nexustcg.vxqrmk"
        const val NEXUS_TCG_HANDSHAKE_URI =
            "nexus-tcg-engine://open?section=settings&hubSync=1&from=tcgstudio"
    }

    fun detectNexusTcgClient(): InstalledCompanionAppInfo {
        val pm = context.packageManager
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(NEXUS_TCG_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(NEXUS_TCG_PACKAGE, 0)
            }
        }.getOrNull()

        val probeIntent = buildNexusTcgHandshakeIntent()
        val canHandleLink = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(probeIntent, PackageManager.ResolveInfoFlags.of(0)).isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probeIntent, 0).isNotEmpty()
            }
        }.getOrDefault(false)

        if (pkgInfo == null) {
            return InstalledCompanionAppInfo(
                packageName = NEXUS_TCG_PACKAGE,
                isInstalled = false,
                versionName = null,
                versionCode = null,
                canHandleDeepLink = canHandleLink,
            )
        }

        val versionCodeLong = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        return InstalledCompanionAppInfo(
            packageName = NEXUS_TCG_PACKAGE,
            isInstalled = true,
            versionName = pkgInfo.versionName,
            versionCode = versionCodeLong,
            canHandleDeepLink = canHandleLink,
        )
    }

    fun buildNexusTcgHandshakeIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(NEXUS_TCG_HANDSHAKE_URI)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
