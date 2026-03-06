package com.dlynce.fittododia.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
fun AppScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    val selectedTopRoute = when {
        currentRoute.startsWith(Route.Home.path) -> Route.Home.path
        currentRoute.startsWith(Route.Agenda.path) ||
                currentRoute.startsWith("editWorkout") ||
                currentRoute.startsWith("library") ||
                currentRoute.startsWith("addExercise") -> Route.Agenda.path
        currentRoute.startsWith(Route.Treino.path) -> Route.Treino.path
        currentRoute.startsWith(Route.Progresso.path) -> Route.Progresso.path
        currentRoute.startsWith(Route.Settings.path) -> Route.Settings.path
        else -> Route.Home.path
    }

    val showBottomBar = currentRoute != Route.Onboarding.path

    Scaffold(
        bottomBar = {
            if (showBottomBar) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {

                    BottomBar(
                        selectedTopRoute = selectedTopRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Composable
private fun BottomBar(
    selectedTopRoute: String?,
    onNavigate: (String) -> Unit
) {

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = if (isLight) 4.dp else 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth(0.9f) // largura menor
            .height(70.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            NavIcon(
                selected = selectedTopRoute == Route.Home.path,
                icon = Icons.Filled.Home,
                onClick = { onNavigate(Route.Home.path) }
            )

            NavIcon(
                selected = selectedTopRoute == Route.Agenda.path,
                icon = Icons.Filled.CalendarToday,
                onClick = { onNavigate(Route.Agenda.path) }
            )

            TreinoFab(
                selected = selectedTopRoute == Route.Treino.path,
                onClick = { onNavigate(Route.Treino.path) }
            )

            NavIcon(
                selected = selectedTopRoute == Route.Progresso.path,
                icon = Icons.Filled.CheckCircle,
                onClick = { onNavigate(Route.Progresso.path) }
            )

            NavIcon(
                selected = selectedTopRoute == Route.Settings.path,
                icon = Icons.Filled.Settings,
                onClick = { onNavigate(Route.Settings.path) }
            )
        }
    }
}

@Composable
private fun NavIcon(
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {

    IconButton(onClick = onClick) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TreinoFab(
    selected: Boolean,
    onClick: () -> Unit
) {

    FloatingActionButton(
        onClick = onClick,
        containerColor = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.primaryContainer
    ) {

        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = "Treino"
        )
    }
}