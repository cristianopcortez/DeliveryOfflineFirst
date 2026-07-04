package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository

/**
 * No-op test double for [AnalyticsRepository].
 *
 * Eliminates the FirebaseAnalytics dependency from instrumented tests so events are
 * not sent to a real Firebase project during test runs. ViewModel calls to analytics
 * methods succeed silently without any assertions — tests focus on state and UI, not
 * on analytics side-effects.
 */
class FakeAnalyticsRepository : AnalyticsRepository {
    override fun logNlpConfigFetched(nlpEnabled: Boolean, trigger: String) = Unit
    override fun logNlpCommandSubmitted() = Unit
    override fun logNlpCommandResult(action: String, success: Boolean) = Unit
    override fun setNlpFeatureUserProperty(enabled: Boolean) = Unit
    override fun logSyncCompleted(quantity: Int) = Unit
}
