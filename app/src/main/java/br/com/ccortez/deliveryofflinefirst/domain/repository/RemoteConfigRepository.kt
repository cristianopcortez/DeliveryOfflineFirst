package br.com.ccortez.deliveryofflinefirst.domain.repository

interface RemoteConfigRepository {
    /**
     * Fetches the latest Remote Config values from Firebase and returns
     * whether the NLP / Gemini feature is enabled for this build.
     *
     * Falls back to the in-app default (true) when the network is unavailable
     * or the fetch fails, so the feature is never silently lost on first install.
     */
    suspend fun isNlpEnabled(): Boolean
}
