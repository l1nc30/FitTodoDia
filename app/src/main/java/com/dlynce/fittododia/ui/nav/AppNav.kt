package com.dlynce.fittododia.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.dlynce.fittododia.ui.screens.OnboardingScreen
import com.dlynce.fittododia.ui.screens.ProgressoScreen
import com.dlynce.fittododia.ui.screens.ProgramDetailScreen
import com.dlynce.fittododia.ui.screens.ProgramsScreen
import com.dlynce.fittododia.ui.screens.SettingsScreen
import com.dlynce.fittododia.ui.screens.TreinoScreen

@Composable
fun AppNav(
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val onboardingDone by settingsViewModel.onboardingDone.collectAsStateWithLifecycle()

    // startDestination: onboarding para novos usuários, home para quem já viu
    val startDestination = if (onboardingDone) Route.Home.path else Route.Onboarding.path

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
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {

            // --- Onboarding (só na primeira vez) ---
            composable(Route.Onboarding.path) {
                OnboardingScreen(
                    onFinish = {
                        settingsViewModel.completeOnboarding()
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Onboarding.path) { inclusive = true }
                        }
                    }
                )
            }

            // --- Tabs principais ---
            composable(Route.Home.path) {
                HomeScreen(
                    onGoTreino = { navigateTab(Route.Treino.path) },
                    onGoAgenda = { navigateTab(Route.Agenda.path) },
                    onGoProgresso = { navigateTab(Route.Progresso.path) },
                    onGoPrograms = { navController.navigate(Route.Programs.path) }
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
                    onNavigateToProgress = { navigateTab(Route.Progresso.path) }
                )
            }

            composable(Route.Progresso.path) { ProgressoScreen() }

            composable(Route.Settings.path) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }

            composable(Route.Programs.path) {
                ProgramsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProgram = { programId ->
                        navController.navigate(Route.ProgramDetail.create(programId))
                    }
                )
            }

            composable(
                route = Route.ProgramDetail.path,
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) { backStackEntry ->
                val programId = backStackEntry.arguments?.getString("programId") ?: ""
                ProgramDetailScreen(
                    programId = programId,
                    onBack = { navController.popBackStack() },
                    onAppliedGoAgenda = { navigateTab(Route.Agenda.path) }
                )
            }

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
