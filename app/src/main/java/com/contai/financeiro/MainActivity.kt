package com.contai.financeiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ContaiApp()
        }
    }
}

@Composable
fun ContaiApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Contai",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Seu assessor financeiro pessoal",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Saldo disponível",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    text = "R$ 0,00",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Olá! 👋",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "O Contai está pronto para ajudar você a organizar sua vida financeira."
                        )
                    }
                }
            }
        }
    }
}
