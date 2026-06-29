package br.com.ccortez.deliveryofflinefirst.data.repository

import android.os.Bundle
import android.util.Log
import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsRepositoryImpl(
    private val analytics: FirebaseAnalytics
) : AnalyticsRepository {

    override fun logNlpConfigFetched(nlpEnabled: Boolean, trigger: String) {
        Log.d(TAG, "logNlpConfigFetched → event=$EVENT_NLP_CONFIG_FETCHED | nlpEnabled=$nlpEnabled | trigger=$trigger")
        analytics.logEvent(EVENT_NLP_CONFIG_FETCHED, Bundle().apply {
            putString(PARAM_NLP_ENABLED, nlpEnabled.toString())
            putString(PARAM_TRIGGER, trigger)
        })
    }

    override fun logNlpCommandSubmitted() {
        Log.d(TAG, "logNlpCommandSubmitted → event=$EVENT_NLP_COMMAND_SUBMITTED")
        analytics.logEvent(EVENT_NLP_COMMAND_SUBMITTED, null)
    }

    override fun logNlpCommandResult(action: String, success: Boolean) {
        Log.d(TAG, "logNlpCommandResult → event=$EVENT_NLP_COMMAND_RESULT | action=$action | success=$success")
        analytics.logEvent(EVENT_NLP_COMMAND_RESULT, Bundle().apply {
            putString(PARAM_ACTION, action)
            putString(PARAM_SUCCESS, success.toString())
        })
    }

    override fun setNlpFeatureUserProperty(enabled: Boolean) {
        Log.d(TAG, "setNlpFeatureUserProperty → property=$USER_PROP_NLP_ENABLED | value=$enabled")
        analytics.setUserProperty(USER_PROP_NLP_ENABLED, enabled.toString())
    }

    override fun logSyncCompleted(quantity: Int) {
        Log.d(TAG, "logSyncCompleted → event=$EVENT_SYNC_COMPLETED | quantity=$quantity")
        analytics.logEvent(EVENT_SYNC_COMPLETED, Bundle().apply {
            putLong(PARAM_QUANTITY, quantity.toLong())
        })
    }

    companion object {
        private const val TAG = "DEBUG_OFFLINE_FIRST"

        // Event names — max 40 chars each
        const val EVENT_NLP_CONFIG_FETCHED    = "nlp_config_fetched"
        const val EVENT_NLP_COMMAND_SUBMITTED = "nlp_command_submitted"
        const val EVENT_NLP_COMMAND_RESULT    = "nlp_command_result"
        const val EVENT_SYNC_COMPLETED        = "sync_completed"

        // Parameter names — max 40 chars each
        const val PARAM_NLP_ENABLED = "nlp_enabled"
        const val PARAM_TRIGGER     = "trigger"
        const val PARAM_ACTION      = "action"
        const val PARAM_SUCCESS     = "success"
        const val PARAM_QUANTITY    = "quantity"

        // User property — max 24 chars
        const val USER_PROP_NLP_ENABLED = "nlp_feature_enabled"

        // Trigger values
        const val TRIGGER_INIT          = "init"
        const val TRIGGER_RESUME        = "resume"
        const val TRIGGER_MANUAL_RELOAD = "manual_reload"

        // Action values (for logNlpCommandResult)
        const val ACTION_SET_SEARCH_QUERY  = "set_search_query"
        const val ACTION_CONCLUDE_DELIVERY = "conclude_delivery"
        const val ACTION_UNKNOWN           = "unknown"
    }
}
