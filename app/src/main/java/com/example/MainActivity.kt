package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.TcgStudioApplication
import com.example.ui.HubDeepLinkRequest
import com.example.ui.StudioViewModel
import com.example.ui.TcgStudioApp
import com.example.ui.nexus.EngineSyncViewModel
import com.example.ui.nexus.NexusHubViewModel

class MainActivity : ComponentActivity() {
    private var pendingHubDeepLink by mutableStateOf<HubDeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        pendingHubDeepLink = parseHubDeepLink(intent)

        val container = (application as TcgStudioApplication).container
        setContent {
            val studioViewModel: StudioViewModel = viewModel(
                factory = StudioViewModel.factory(container),
            )
            val nexusHubViewModel: NexusHubViewModel = viewModel(
                factory = NexusHubViewModel.factory(container),
            )
            val engineSyncViewModel: EngineSyncViewModel = viewModel(
                factory = EngineSyncViewModel.factory(container),
            )
            val settings by studioViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.darkTheme) {
                if (settings.darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    )
                }
            }
            TcgStudioApp(
                viewModel = studioViewModel,
                nexusHubViewModel = nexusHubViewModel,
                engineSyncViewModel = engineSyncViewModel,
                pendingHubDeepLink = pendingHubDeepLink,
                onHubDeepLinkConsumed = { pendingHubDeepLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseHubDeepLink(intent)?.let { req ->
            pendingHubDeepLink = req
        }
    }

    private fun parseHubDeepLink(intent: Intent?): HubDeepLinkRequest? {
        if (intent == null) return null
        val uri = intent.data
        val isSchemeMatch = uri != null &&
            uri.scheme.equals("tcgstudio", ignoreCase = true) &&
            uri.host.equals("nexus-hub", ignoreCase = true)

        val hubSyncParam = uri?.getQueryParameter("hubSync")
        val hubSync = hubSyncParam == "1" ||
            hubSyncParam.equals("true", ignoreCase = true) ||
            intent.getBooleanExtra("hubSync", false)

        val exportParam = uri?.getQueryParameter("exportClientInfo")
        val exportClientInfo = exportParam == "1" ||
            exportParam.equals("true", ignoreCase = true) ||
            intent.getBooleanExtra("exportClientInfo", false)

        val fromApp = uri?.getQueryParameter("from")?.trim()?.ifBlank { null }
            ?: intent.getStringExtra("from")?.trim()?.ifBlank { null }

        if (!isSchemeMatch && !hubSync && !exportClientInfo && fromApp == null) {
            return null
        }
        return HubDeepLinkRequest(
            hubSync = hubSync || fromApp.equals("nexustcg", ignoreCase = true),
            exportClientInfo = exportClientInfo,
            fromApp = fromApp,
        )
    }
}
