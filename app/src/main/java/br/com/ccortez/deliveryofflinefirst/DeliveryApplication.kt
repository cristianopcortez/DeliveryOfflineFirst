package br.com.ccortez.deliveryofflinefirst

import android.app.Application
import android.util.Base64
import androidx.core.content.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
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
     *
     * **File name format (Firebase App Check Debug SDK >= 18.0):** The SDK switched to a
     * per-app SharedPreferences file named
     * `com.google.firebase.appcheck.debug.store.{base64(appName)}+{base64(appId)}`
     * with the key `com.google.firebase.appcheck.debug.DEBUG_SECRET`.
     * We construct this name from the live [FirebaseApp] instance so the code stays correct
     * even if the Firebase App name or App ID ever change.
     */
    private fun pinAppCheckDebugTokenIfConfigured() {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (token.isBlank()) return

        val flags = Base64.NO_PADDING or Base64.NO_WRAP
        fun String.b64() = Base64.encodeToString(toByteArray(Charsets.UTF_8), flags)

        val firebaseApp = FirebaseApp.getInstance()
        val perAppPrefsName =
            "com.google.firebase.appcheck.debug.store.${firebaseApp.name.b64()}+${firebaseApp.options.applicationId.b64()}"

        // Write to the current per-app file (Firebase >= 18) and the legacy flat file,
        // covering both key names to be resilient across SDK version changes.
        mapOf(
            perAppPrefsName to "com.google.firebase.appcheck.debug.DEBUG_SECRET",
            "com.google.firebase.appcheck.debug.store" to "firebase_app_check_debug_secret",
        ).forEach { (prefsName, key) ->
            getSharedPreferences(prefsName, MODE_PRIVATE).edit { putString(key, token) }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
