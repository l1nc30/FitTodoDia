package com.dlynce.fittododia.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dlynce.fittododia.settings.SettingsViewModel
import com.dlynce.fittododia.ui.screens.AddExerciseScreen
import com.dlynce.fittododia.ui.screens.AgendaScreen
import com.dlynce.fittododia.ui.screens.EditWorkoutScreen
import com.dlynce.fittododia.ui.screens.ExerciseLibraryScreen
import com.dlynce.fittododia.ui.screens.HomeScreen
import com.dlynce.fittododia.ui.screens.PerfilScreen
import com.dlynce.fittododia.ui.screens.ProgressoScreen
import com.dlynce.fittododia.ui.screens.TreinoScreen

@Composable
fun AppNav(
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    // helper: navegação de TAB (não empilha e preserva estado)
    fun navigateTab(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    AppScaffold(navController = navController) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(innerPadding)
        ) {
            // --- Tabs principais ---
            composable(Route.Home.path) {
                HomeScreen(
                    onGoTreino = { navigateTab(Route.Treino.path) },
                    onGoAgenda = { navigateTab(Route.Agenda.path) },
                    onGoProgresso = { navigateTab(Route.Progresso.path) }
                )
            }

            composable(Route.Agenda.path) {
                AgendaScreen(
                    onDayClick = { dayId ->
                        navController.navigate(Route.EditWorkout.create(dayId))
                    }
                )
            }

            composable(Route.Treino.path) {
                TreinoScreen(
                    onNavigateToProgress = {
                        // Treino agora é TAB: não remova ele do backstack
                        navigateTab(Route.Progresso.path)
                    }
                )
            }

            composable(Route.Progresso.path) { ProgressoScreen() }

            composable(Route.Perfil.path) {
                PerfilScreen(settingsViewModel = settingsViewModel)
            }

            // --- Editar treino do dia ---
            composable(
                route = Route.EditWorkout.path,
                arguments = listOf(navArgument("dayId") { type = NavType.IntType })
            ) { backStackEntry ->
                val dayId = backStackEntry.arguments?.getInt("dayId") ?: 1

                EditWorkoutScreen(
                    dayId = dayId,
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = { navController.navigate(Route.Library.create(dayId)) }
                )
            }

            // --- Biblioteca (seleciona exercício) ---
            composable(
                route = Route.Library.path,
                arguments = listOf(navArgument("dayId") { type = NavType.IntType })
            ) { backStackEntry ->
                val dayId = backStackEntry.arguments?.getInt("dayId") ?: 1

                ExerciseLibraryScreen(
                    dayId = dayId,
                    onBack = { navController.popBackStack() },
                    onSelected = { exerciseId ->
                        navController.navigate(Route.AddExercise.create(dayId, exerciseId))
                    }
                )
            }

            // --- Configurar exercício e adicionar ao treino do dia ---
            composable(
                route = Route.AddExercise.path,
                arguments = listOf(
                    navArgument("dayId") { type = NavType.IntType },
                    navArgument("exerciseId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val dayId = backStackEntry.arguments?.getInt("dayId") ?: 1
                val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: 0L

                AddExerciseScreen(
                    dayId = dayId,
                    exerciseId = exerciseId,
                    onDone = {
                        navController.popBackStack(
                            route = Route.EditWorkout.create(dayId),
                            inclusive = false
                        )
                    }
                )
            }
        }
    }
}
