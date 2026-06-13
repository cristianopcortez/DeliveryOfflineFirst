package br.com.ccortez.deliveryofflinefirst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.presentation.screen.EntregasScreen
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel
import br.com.ccortez.deliveryofflinefirst.ui.theme.DeliveryOfflineFirstTheme

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { EntregaRepositoryImpl(db.entregaDao()) }

    private val viewModel: EntregasViewModel by viewModels {
        EntregasViewModel.factory(repository)
    }

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
