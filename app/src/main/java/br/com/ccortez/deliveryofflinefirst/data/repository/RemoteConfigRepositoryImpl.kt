package br.com.ccortez.deliveryofflinefirst.data.repository

import android.util.Log
import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await

class RemoteConfigRepositoryImpl(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigRepository {

    override suspend fun isNlpEnabled(): Boolean {
        Log.d(TAG, "isNlpEnabled → starting fetchAndActivate()")
        return try {
            val activated = remoteConfig.fetchAndActivate().await()
            val value = remoteConfig.getBoolean(KEY_NLP_ENABLED)
            Log.d(TAG, "isNlpEnabled → fetchAndActivate completed | activated=$activated | $KEY_NLP_ENABLED=$value")
            value
        } catch (e: Exception) {
            val cached = remoteConfig.getBoolean(KEY_NLP_ENABLED)
            Log.d(TAG, "isNlpEnabled → fetchAndActivate failed (${e.javaClass.simpleName}: ${e.message}) | using cache/default: $KEY_NLP_ENABLED=$cached")
            cached
        }
    }

    companion object {
        private const val TAG = "DEBUG_OFFLINE_FIRST"
        const val KEY_NLP_ENABLED = "nlp_enabled"
    }
}
