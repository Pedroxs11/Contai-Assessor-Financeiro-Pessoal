package com.contai.financeiro

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ContaiDestination(val label: String) {
    HOME("Início"),
    AGENDA("Agenda"),
    REPORTS("Relatórios"),
    PROFILE("Perfil")
}

@Composable
fun ContaiBottomNavigation(
    selected: ContaiDestination,
    onDestinationSelected: (ContaiDestination) -> Unit,
    onAddClick: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == ContaiDestination.HOME,
            onClick = { onDestinationSelected(ContaiDestination.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Início") },
            label = { Text("Início") }
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.AGENDA,
            onClick = { onDestinationSelected(ContaiDestination.AGENDA) },
            icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Agenda") },
            label = { Text("Agenda") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onAddClick,
            icon = {
                FloatingActionButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar")
                }
            },
            alwaysShowLabel = false,
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.REPORTS,
            onClick = { onDestinationSelected(ContaiDestination.REPORTS) },
            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Relatórios") },
            label = { Text("Relatórios") }
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.PROFILE,
            onClick = { onDestinationSelected(ContaiDestination.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Perfil") },
            label = { Text("Perfil") }
        )
    }
}
