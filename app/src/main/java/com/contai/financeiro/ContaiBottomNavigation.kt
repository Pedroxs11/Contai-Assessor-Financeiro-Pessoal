package com.contai.financeiro

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val navigationColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = Color.Transparent
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selected == ContaiDestination.HOME,
            onClick = { onDestinationSelected(ContaiDestination.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Início") },
            label = { Text("Início") },
            colors = navigationColors
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.AGENDA,
            onClick = { onDestinationSelected(ContaiDestination.AGENDA) },
            icon = { Icon(Icons.Filled.DateRange, contentDescription = "Agenda") },
            label = { Text("Agenda") },
            colors = navigationColors
        )
        NavigationBarItem(
            selected = false,
            onClick = onAddClick,
            icon = {
                FloatingActionButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(58.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Adicionar",
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            alwaysShowLabel = false,
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.REPORTS,
            onClick = { onDestinationSelected(ContaiDestination.REPORTS) },
            icon = { Icon(Icons.Filled.List, contentDescription = "Relatórios") },
            label = { Text("Relatórios") },
            colors = navigationColors
        )
        NavigationBarItem(
            selected = selected == ContaiDestination.PROFILE,
            onClick = { onDestinationSelected(ContaiDestination.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Perfil") },
            label = { Text("Perfil") },
            colors = navigationColors
        )
    }
}
