package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.TcgStudioApplication
import com.example.ui.StudioViewModel
import com.example.ui.TcgStudioApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TcgStudioApplication).container
        setContent {
            val studioViewModel: StudioViewModel = viewModel(
                factory = StudioViewModel.factory(container),
            )
            TcgStudioApp(viewModel = studioViewModel)
        }
    }
}
