package br.com.ccortez.deliveryofflinefirst.domain.repository

import br.com.ccortez.deliveryofflinefirst.data.local.datastore.SettingsConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<SettingsConfig>
    suspend fun updateDarkTheme(enabled: Boolean)
    suspend fun updateMotoristaNome(nome: String)
}
