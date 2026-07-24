package com.pontat.registreboucles.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pontat.registreboucles.ui.screens.ConfigScreen
import com.pontat.registreboucles.ui.screens.DebugScreen
import com.pontat.registreboucles.ui.screens.JournalScreen
import com.pontat.registreboucles.ui.screens.ListeScreen
import com.pontat.registreboucles.ui.screens.SupervisionScreen

object Routes {
    const val LISTE = "liste"
    const val DEBUG = "debug"
    const val CONFIG = "config"
    const val JOURNAL = "journal/{id}"
    const val SUPERVISION = "supervision"
    fun journal(id: String) = "journal/$id"
}

@Composable
fun RegistreNavHost(vm: BoucleViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LISTE) {
        // La création est une bottom sheet dans l'écran Liste (pas une route).
        composable(Routes.LISTE) {
            ListeScreen(
                vm = vm,
                onOuvrirDebug = { nav.navigate(Routes.DEBUG) },
                onOuvrirConfig = { nav.navigate(Routes.CONFIG) },
                onOuvrirJournal = { id -> nav.navigate(Routes.journal(id)) },
                onOuvrirSupervision = { nav.navigate(Routes.SUPERVISION) }
            )
        }
        composable(Routes.SUPERVISION) {
            SupervisionScreen(vm = vm, onRetour = { nav.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugScreen(onRetour = { nav.popBackStack() })
        }
        composable(Routes.CONFIG) {
            ConfigScreen(vm = vm, onRetour = { nav.popBackStack() })
        }
        composable(
            route = Routes.JOURNAL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            JournalScreen(
                vm = vm,
                boucleId = entry.arguments?.getString("id").orEmpty(),
                onRetour = { nav.popBackStack() }
            )
        }
    }
}
