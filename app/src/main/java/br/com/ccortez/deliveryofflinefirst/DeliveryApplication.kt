package br.com.ccortez.deliveryofflinefirst

import android.app.Application
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
        // Firebase is auto-initialised by google-services.json via FirebaseInitProvider.
        // In debug builds, install the DebugAppCheckProviderFactory so the Gemini AI
        // (and other App-Check-enforced services) accept calls from the emulator/debug APK.
        //
        // SETUP — one-time per machine:
        //   1. Run the app once and filter Logcat by "DebugAppCheckProvider".
        //   2. Copy the UUID printed there.
        //   3. Firebase Console → Build → App Check → your app → Manage debug tokens → Add.
        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
