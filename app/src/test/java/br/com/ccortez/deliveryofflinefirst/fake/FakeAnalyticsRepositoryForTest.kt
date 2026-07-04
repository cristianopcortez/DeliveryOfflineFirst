package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository

class FakeAnalyticsRepositoryForTest : AnalyticsRepository {
    override fun logNlpConfigFetched(nlpEnabled: Boolean, trigger: String) = Unit
    override fun logNlpCommandSubmitted() = Unit
    override fun logNlpCommandResult(action: String, success: Boolean) = Unit
    override fun setNlpFeatureUserProperty(enabled: Boolean) = Unit
    override fun logSyncCompleted(quantity: Int) = Unit
}
