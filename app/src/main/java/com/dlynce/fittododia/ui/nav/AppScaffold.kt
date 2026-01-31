package com.dlynce.fittododia.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    // ✅ Seleção correta mesmo com rotas que têm args (editWorkout/3, library/2, addExercise/1/10)
    val selectedTopRoute = when {
        currentRoute.startsWith(Route.Home.path) -> Route.Home.path

        currentRoute.startsWith(Route.Agenda.path) ||
                currentRoute.startsWith("editWorkout") ||
                currentRoute.startsWith("library") ||
                currentRoute.startsWith("addExercise") -> Route.Agenda.path

        currentRoute.startsWith(Route.Treino.path) -> Route.Treino.path
        currentRoute.startsWith(Route.Progresso.path) -> Route.Progresso.path
        currentRoute.startsWith(Route.Perfil.path) -> Route.Perfil.path

        else -> Route.Home.path
    }

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        // ✅ repassa o padding para o NavHost (no AppNav) não ficar por baixo da barra
        content(innerPadding)
    }
}

@Composable
private fun BottomBar(
    selectedTopRoute: String?,
    onNavigate: (String) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    // ✅ Tema claro: sólido (sem 2 tons de branco). Tema escuro: "glass" translúcido.
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val barColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }

    Surface(
        tonalElevation = if (isLight) 2.dp else 0.dp,
        shadowElevation = if (isLight) 6.dp else 10.dp,
        color = barColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 6.dp),
            // ✅ no claro não deixa transparente, evita “camadas” de branco
            containerColor = if (isLight) barColor else Color.Transparent
        ) {
            NavItem(
                selected = selectedTopRoute == Route.Home.path,
                label = "Home",
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                onClick = { onNavigate(Route.Home.path) }
            )

            NavItem(
                selected = selectedTopRoute == Route.Agenda.path,
                label = "Agenda",
                icon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Agenda") },
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                onClick = { onNavigate(Route.Agenda.path) }
            )

            NavItem(
                selected = selectedTopRoute == Route.Treino.path,
                label = "Treino",
                icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = "Treino") },
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                onClick = { onNavigate(Route.Treino.path) }
            )

            NavItem(
                selected = selectedTopRoute == Route.Progresso.path,
                label = "Progresso",
                icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Progresso") },
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                onClick = { onNavigate(Route.Progresso.path) }
            )

            NavItem(
                selected = selectedTopRoute == Route.Perfil.path,
                label = "Perfil",
                icon = { Icon(Icons.Filled.Person, contentDescription = "Perfil") },
                selectedColor = selectedColor,
                unselectedColor = unselectedColor,
                onClick = { onNavigate(Route.Perfil.path) }
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            // ✅ sem pill/indicador atrás do ícone
            indicatorColor = Color.Transparent,
            selectedIconColor = selectedColor,
            selectedTextColor = selectedColor,
            unselectedIconColor = unselectedColor,
            unselectedTextColor = unselectedColor
        )
    )
}
