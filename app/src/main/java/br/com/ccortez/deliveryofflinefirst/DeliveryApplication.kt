package br.com.ccortez.deliveryofflinefirst

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DeliveryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            pinAppCheckDebugTokenIfConfigured()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
    }

    /**
     * Pre-populates the SharedPreferences slot that [DebugAppCheckProviderFactory] reads
     * before generating a new UUID, effectively pinning the debug token to the value in
     * `local.properties → APP_CHECK_DEBUG_TOKEN`.
     *
     * **Why this is needed:** [DebugAppCheckProviderFactory] stores its UUID in
     * SharedPreferences. Any operation that clears app data — uninstall, "Clear data"
     * from Settings, or certain CI test runners — deletes that UUID, causing the SDK to
     * generate a new one that must then be re-registered in Firebase Console.
     * By pre-populating the slot here (before the factory is installed), we ensure the
     * same UUID is always used, regardless of reinstalls.
     *
     * **Token is empty?** The method is a no-op: the SDK auto-generates a UUID and prints
     * it to Logcat (filter `DebugAppCheckProvider`). Copy that UUID, add it to
     * `local.properties` as `APP_CHECK_DEBUG_TOKEN=<uuid>`, and register it once in
     * Firebase Console → Build → App Check → Manage debug tokens.
     */
    private fun pinAppCheckDebugTokenIfConfigured() {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (token.isBlank()) return

        // The Firebase App Check Debug SDK stores the secret in SharedPreferences.
        // We write to all known candidate file names to be resilient to SDK version changes.
        listOf(
            "com.google.firebase.appcheck.debug.store",
            "com.google.firebase.appcheck",
        ).forEach { prefsName ->
            getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putString("firebase_app_check_debug_secret", token)
                .apply()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
