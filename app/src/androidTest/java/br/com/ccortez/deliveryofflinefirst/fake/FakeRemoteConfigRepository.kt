package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository

/**
 * Test double for [RemoteConfigRepository].
 *
 * Eliminates the Firebase Remote Config network dependency in instrumented tests:
 * no fetches, no caching, fully deterministic. Inject via Hilt's @BindValue or a
 * test @Module to control `nlp_enabled` per test class.
 */
class FakeRemoteConfigRepository(private val nlpEnabled: Boolean) : RemoteConfigRepository {
    override suspend fun isNlpEnabled(): Boolean = nlpEnabled
}
