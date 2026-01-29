package com.dlynce.fittododia.ui.nav

import androidx.compose.runtime.Composable
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

    AppScaffold(navController = navController) {
        NavHost(
            navController = navController,
            startDestination = Route.Home.path
        ) {
            // --- Tabs principais ---
            composable(Route.Home.path) {
                HomeScreen(
                    onGoTreino = { navController.navigate(Route.Treino.path) },
                    onGoAgenda = { navController.navigate(Route.Agenda.path) },
                    onGoProgresso = { navController.navigate(Route.Progresso.path) }
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
                        navController.navigate(Route.Progresso.path) {
                            launchSingleTop = true
                            popUpTo(Route.Treino.path) { inclusive = true }
                        }
                    }
                )
            }

            composable(Route.Progresso.path) { ProgressoScreen() }

            // ✅ aqui passa o SettingsViewModel pro Perfil
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
                    onOpenLibrary = {
                        navController.navigate(Route.Library.create(dayId))
                    }
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
