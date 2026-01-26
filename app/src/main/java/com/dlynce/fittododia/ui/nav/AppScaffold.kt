package com.dlynce.fittododia.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppScaffold(
    navController: NavController,
    content: @Composable () -> Unit
) {
    val backStack = navController.currentBackStackEntryAsState()
    val current = backStack.value?.destination?.route

    val items = listOf(
        Route.Home to "Hoje",
        Route.Agenda to "Agenda",
        Route.Treino to "Treino",
        Route.Progresso to "Progresso",
        Route.Perfil to "Perfil",
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { (route, label) ->
                    NavigationBarItem(
                        selected = current == route.path,
                        onClick = {
                            if (current != route.path) {
                                navController.navigate(route.path) {
                                    popUpTo(Route.Home.path) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        label = { Text(label) },
                        icon = { /* MVP: sem ícones por enquanto */ }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
