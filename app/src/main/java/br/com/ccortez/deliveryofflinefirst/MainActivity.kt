package br.com.ccortez.deliveryofflinefirst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.ccortez.deliveryofflinefirst.presentation.screen.EntregasScreen
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel
import br.com.ccortez.deliveryofflinefirst.ui.theme.DeliveryOfflineFirstTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: EntregasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeliveryOfflineFirstTheme {
                EntregasScreen(viewModel = viewModel)
            }
        }
    }
}
