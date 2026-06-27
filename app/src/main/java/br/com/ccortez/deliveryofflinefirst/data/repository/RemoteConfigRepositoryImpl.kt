package br.com.ccortez.deliveryofflinefirst.data.repository

import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await

class RemoteConfigRepositoryImpl(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigRepository {

    override suspend fun isNlpEnabled(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getBoolean(KEY_NLP_ENABLED)
        } catch (_: Exception) {
            // Network unavailable or quota exceeded — return whatever is cached/default
            remoteConfig.getBoolean(KEY_NLP_ENABLED)
        }
    }

    companion object {
        const val KEY_NLP_ENABLED = "nlp_enabled"
    }
}
