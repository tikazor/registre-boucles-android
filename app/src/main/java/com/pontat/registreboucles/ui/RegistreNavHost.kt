package com.pontat.registreboucles.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pontat.registreboucles.ui.screens.DebugScreen
import com.pontat.registreboucles.ui.screens.ListeScreen

object Routes {
    const val LISTE = "liste"
    const val DEBUG = "debug"
}

@Composable
fun RegistreNavHost(vm: BoucleViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LISTE) {
        // La création est une bottom sheet dans l'écran Liste (pas une route).
        composable(Routes.LISTE) {
            ListeScreen(
                vm = vm,
                onOuvrirDebug = { nav.navigate(Routes.DEBUG) }
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(onRetour = { nav.popBackStack() })
        }
    }
}
