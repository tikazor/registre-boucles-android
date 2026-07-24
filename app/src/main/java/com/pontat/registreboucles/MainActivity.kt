package com.pontat.registreboucles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.RegistreNavHost
import com.pontat.registreboucles.ui.screens.ImportScreen
import com.pontat.registreboucles.ui.theme.RegistreBouclesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as RegistreApplication).repository
        setContent {
            val vm: BoucleViewModel = viewModel(factory = BoucleViewModel.Factory(repository))
            val sombre by vm.modeSombre.collectAsStateWithLifecycle()
            RegistreBouclesTheme(darkTheme = sombre) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(vm)
                }
            }
        }
    }
}

@Composable
private fun App(vm: BoucleViewModel) {
    val baseVide by vm.baseVide.collectAsStateWithLifecycle()
    when (baseVide) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        true -> ImportScreen(vm = vm)
        false -> RegistreNavHost(vm = vm)
    }
}
