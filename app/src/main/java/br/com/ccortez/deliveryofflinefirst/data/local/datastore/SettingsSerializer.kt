package br.com.ccortez.deliveryofflinefirst.data.local.datastore

import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<SettingsConfig> {

    override val defaultValue: SettingsConfig = SettingsConfig()

    override suspend fun readFrom(input: InputStream): SettingsConfig {
        return try {
            Json.decodeFromString(
                deserializer = SettingsConfig.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (e: SerializationException) {
            // Corrupted file — return defaults rather than crashing
            defaultValue
        }
    }

    override suspend fun writeTo(t: SettingsConfig, output: OutputStream) {
        output.write(
            Json.encodeToString(
                serializer = SettingsConfig.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }
}
