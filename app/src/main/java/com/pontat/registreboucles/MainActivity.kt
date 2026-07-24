package com.pontat.registreboucles

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontat.registreboucles.ui.BoucleViewModel
import com.pontat.registreboucles.ui.CibleWidget
import com.pontat.registreboucles.ui.RegistreNavHost
import com.pontat.registreboucles.ui.screens.ImportScreen
import com.pontat.registreboucles.ui.theme.RegistreBouclesTheme

class MainActivity : ComponentActivity() {

    // Deep-link en attente (issu d'un tap sur le widget).
    private var deepLink by mutableStateOf<CibleWidget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = parseDeepLink(intent)
        val repository = (application as RegistreApplication).repository
        setContent {
            val vm: BoucleViewModel = viewModel(factory = BoucleViewModel.Factory(repository))

            // Applique le deep-link au VM (déplie / ouvre le mouvement).
            LaunchedEffect(deepLink) {
                deepLink?.let {
                    vm.ciblerDepuisWidget(it.boucleId, it.ouvrirMouvement)
                    deepLink = null
                }
            }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = parseDeepLink(intent)
    }

    /** registreboucles://boucle/{id}?mvt=1 */
    private fun parseDeepLink(intent: Intent?): CibleWidget? {
        val data = intent?.data ?: return null
        if (data.scheme != "registreboucles" || data.host != "boucle") return null
        val id = data.lastPathSegment ?: return null
        val mvt = data.getQueryParameter("mvt") == "1"
        return CibleWidget(id, mvt)
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
