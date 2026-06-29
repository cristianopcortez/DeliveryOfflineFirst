package br.com.ccortez.deliveryofflinefirst.domain.repository

/**
 * Contract for Firebase Analytics event tracking.
 *
 * Keeping analytics behind an interface lets ViewModels stay testable:
 * unit tests inject a no-op fake instead of the real FirebaseAnalytics instance.
 */
interface AnalyticsRepository {

    /**
     * Logged every time Remote Config is fetched and [nlpEnabled] is known.
     *
     * @param nlpEnabled current value of the "nlp_enabled" flag after activation.
     * @param trigger    context that originated the fetch:
     *                   "init"          — first fetch on ViewModel creation,
     *                   "resume"        — re-fetch triggered by lifecycle RESUMED,
     *                   "manual_reload" — user tapped "Reload Remote Config" in Settings.
     *
     * Use this event in the Firebase Analytics dashboard (or BigQuery) to build
     * a funnel that shows how many active users have the flag on vs off over time.
     * The companion user property [setNlpFeatureUserProperty] enables audience
     * segmentation across all events in a session.
     */
    fun logNlpConfigFetched(nlpEnabled: Boolean, trigger: String)

    /**
     * Logged when the user submits a natural-language command to Gemini.
     * Counts raw intent to use the NLP feature regardless of the outcome.
     */
    fun logNlpCommandSubmitted()

    /**
     * Logged after Gemini responds and the resolved [action] is dispatched.
     *
     * @param action  one of: "set_search_query", "conclude_delivery", "unknown".
     * @param success false when [action] is "unknown" or no matching delivery was found.
     */
    fun logNlpCommandResult(action: String, success: Boolean)

    /**
     * Sets the persistent user property "nlp_feature_enabled" so every Analytics
     * event from this device is automatically annotated with whether NLP is on or off.
     * This allows audience-based filtering in GA4 / BigQuery without joining on events.
     */
    fun setNlpFeatureUserProperty(enabled: Boolean)

    /**
     * Logged by [SyncWorker][br.com.ccortez.deliveryofflinefirst.data.worker.SyncWorker] once the outbox has been drained successfully.
     *
     * @param quantity number of deliveries that were marked as synchronised in this run.
     *
     * Use this event to measure the health of the offline-first architecture:
     * - Average [quantity] per sync → how many deliveries accumulate before network returns.
     * - Event frequency → how often users are offline long enough to queue writes.
     * - Zero-quantity runs are never logged (the Worker returns early before this call).
     */
    fun logSyncCompleted(quantity: Int)
}
