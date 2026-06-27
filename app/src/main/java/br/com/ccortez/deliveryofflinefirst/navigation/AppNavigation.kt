package br.com.ccortez.deliveryofflinefirst.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.ccortez.deliveryofflinefirst.presentation.screen.EntregasScreen
import br.com.ccortez.deliveryofflinefirst.presentation.screen.SettingsScreen
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.SettingsViewModel

object Routes {
    const val ENTREGAS = "entregas"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.ENTREGAS,
        modifier = modifier
    ) {
        composable(Routes.ENTREGAS) {
            val entregasViewModel: EntregasViewModel = hiltViewModel()
            val nlpEnabled by entregasViewModel.nlpEnabled.collectAsStateWithLifecycle()
            EntregasScreen(
                viewModel = entregasViewModel,
                motoristaNome = settingsState.motoristaNome,
                nlpEnabled = nlpEnabled,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
