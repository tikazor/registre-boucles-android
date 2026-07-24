package com.pontat.registreboucles.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pontat.registreboucles.ui.screens.CreationScreen
import com.pontat.registreboucles.ui.screens.DebugScreen
import com.pontat.registreboucles.ui.screens.ListeScreen

object Routes {
    const val LISTE = "liste"
    const val CREATION = "creation"
    const val DEBUG = "debug"
}

@Composable
fun RegistreNavHost(vm: BoucleViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LISTE) {

        composable(Routes.LISTE) {
            ListeScreen(
                vm = vm,
                onCreer = { nav.navigate(Routes.CREATION) },
                onOuvrirDebug = { nav.navigate(Routes.DEBUG) }
            )
        }

        // Création : panneau qui glisse depuis la droite (slideInRight / slideOutRight).
        composable(
            route = Routes.CREATION,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(260)
                )
            }
        ) {
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
