package com.pontat.registreboucles.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pontat.registreboucles.ui.screens.CreationScreen
import com.pontat.registreboucles.ui.screens.DebugScreen
import com.pontat.registreboucles.ui.screens.DetailScreen
import com.pontat.registreboucles.ui.screens.ListeScreen

object Routes {
    const val LISTE = "liste"
    const val CREATION = "creation"
    const val DEBUG = "debug"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun RegistreNavHost(vm: BoucleViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LISTE) {

        composable(Routes.LISTE) {
            ListeScreen(
                vm = vm,
                onOuvrirDetail = { id -> nav.navigate(Routes.detail(id)) },
                onCreer = { nav.navigate(Routes.CREATION) },
                onOuvrirDebug = { nav.navigate(Routes.DEBUG) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                vm = vm,
                boucleId = id,
                onRetour = { nav.popBackStack() }
            )
        }

        composable(Routes.CREATION) {
            CreationScreen(
                vm = vm,
                onRetour = { nav.popBackStack() },
                onCree = { nav.popBackStack() }
            )
        }

        composable(Routes.DEBUG) {
            DebugScreen(onRetour = { nav.popBackStack() })
        }
    }
}
