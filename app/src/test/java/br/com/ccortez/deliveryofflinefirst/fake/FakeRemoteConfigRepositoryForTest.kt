package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository

class FakeRemoteConfigRepositoryForTest(private val nlpEnabled: Boolean = true) : RemoteConfigRepository {
    override suspend fun isNlpEnabled(): Boolean = nlpEnabled
}
