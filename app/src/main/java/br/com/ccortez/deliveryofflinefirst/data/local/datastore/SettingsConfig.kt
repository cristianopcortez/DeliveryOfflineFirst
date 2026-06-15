package br.com.ccortez.deliveryofflinefirst.data.local.datastore

import kotlinx.serialization.Serializable

// Proto DataStore with kotlinx.serialization:
// type-safe config object — adding a field with the wrong type is a compile-time error,
// not a silent runtime mismatch as it would be with Preferences string keys.
@Serializable
data class SettingsConfig(
    val darkTheme: Boolean = false,
    val motoristaNome: String = "Motorista"
)
