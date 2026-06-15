package br.com.ccortez.deliveryofflinefirst.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

// Single DataStore instance per process — the delegate guarantees this
val Context.settingsDataStore: DataStore<SettingsConfig> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer
)
