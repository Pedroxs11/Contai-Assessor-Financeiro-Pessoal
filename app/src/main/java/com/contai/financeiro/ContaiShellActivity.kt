package com.contai.financeiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contai.financeiro.ui.theme.ContaiTheme

class ContaiShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContaiTheme {
                ContaiShell()
            }
        }
    }
}

@Composable
private fun ContaiShell() {
    var selectedDestination by remember { mutableStateOf(ContaiDestination.HOME) }

    Scaffold(
        bottomBar = {
            ContaiBottomNavigation(
                selected = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
                onAddClick = { selectedDestination = ContaiDestination.HOME }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedDestination) {
                ContaiDestination.HOME -> ContaiApp()
                ContaiDestination.AGENDA -> DestinationPlaceholder(
                    title = "Agenda",
                    description = "Seus compromissos e lembretes ficarão aqui."
                )
                ContaiDestination.REPORTS -> DestinationPlaceholder(
                    title = "Relatórios",
                    description = "Seus indicadores e análises financeiras ficarão aqui."
                )
                ContaiDestination.PROFILE -> DestinationPlaceholder(
                    title = "Perfil",
                    description = "Configurações, categorias e preferências ficarão aqui."
                )
            }
        }
    }
}

@Composable
private fun DestinationPlaceholder(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}
