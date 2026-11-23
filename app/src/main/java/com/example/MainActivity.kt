package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.TcgStudioApplication
import com.example.ui.StudioViewModel
import com.example.ui.TcgStudioApp
import com.example.ui.nexus.NexusHubViewModel

class MainActivity : ComponentActivity() {
    private var pendingHubSyncHandshake by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingHubSyncHandshake = isHubSyncIntent(intent)

        val container = (application as TcgStudioApplication).container
        setContent {
            val studioViewModel: StudioViewModel = viewModel(
                factory = StudioViewModel.factory(container),
            )
            val nexusHubViewModel: NexusHubViewModel = viewModel(
                factory = NexusHubViewModel.factory(container),
            )
            val engineSyncViewModel: com.example.ui.nexus.EngineSyncViewModel = viewModel(
                factory = com.example.ui.nexus.EngineSyncViewModel.factory(container),
            )
            TcgStudioApp(
                viewModel = studioViewModel,
                nexusHubViewModel = nexusHubViewModel,
                engineSyncViewModel = engineSyncViewModel,
                pendingHubSyncHandshake = pendingHubSyncHandshake,
                onHubSyncHandshakeConsumed = { pendingHubSyncHandshake = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHubSyncIntent(intent)) {
            pendingHubSyncHandshake = true
        }
    }

    private fun isHubSyncIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val uriParam = intent.data?.getQueryParameter("hubSync")
        return uriParam == "1" ||
            uriParam.equals("true", ignoreCase = true) ||
            intent.getBooleanExtra("hubSync", false)
    }
}
