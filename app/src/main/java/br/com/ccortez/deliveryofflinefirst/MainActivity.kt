package br.com.ccortez.deliveryofflinefirst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import br.com.ccortez.deliveryofflinefirst.navigation.AppNavigation
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.SettingsViewModel
import br.com.ccortez.deliveryofflinefirst.ui.theme.DeliveryOfflineFirstTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // SettingsViewModel scoped to Activity — shared across all screens via AppNavigation
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // DataStore Flow drives the theme — changes immediately without restart
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            DeliveryOfflineFirstTheme(darkTheme = settingsState.darkTheme) {
                AppNavigation(settingsViewModel = settingsViewModel)
            }
        }
    }
}
