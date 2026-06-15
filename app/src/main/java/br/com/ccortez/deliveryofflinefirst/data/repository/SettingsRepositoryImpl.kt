package br.com.ccortez.deliveryofflinefirst.data.repository

import androidx.datastore.core.DataStore
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.SettingsConfig
import br.com.ccortez.deliveryofflinefirst.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

class SettingsRepositoryImpl(
    private val dataStore: DataStore<SettingsConfig>
) : SettingsRepository {

    // DataStore.data returns a Flow — reactive, no ANR risk, transactional
    override val settings: Flow<SettingsConfig> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(SettingsConfig()) else throw e
        }

    override suspend fun updateDarkTheme(enabled: Boolean) {
        dataStore.updateData { current -> current.copy(darkTheme = enabled) }
    }

    override suspend fun updateMotoristaNome(nome: String) {
        dataStore.updateData { current -> current.copy(motoristaNome = nome) }
    }
}
