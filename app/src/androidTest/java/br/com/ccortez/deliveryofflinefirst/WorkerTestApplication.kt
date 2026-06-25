package br.com.ccortez.deliveryofflinefirst

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Base Application for instrumented tests that need WorkManager + Hilt.
 *
 * HiltTestApplication (used by default) does not implement Configuration.Provider,
 * so WorkManager falls back to reflection and fails to instantiate @HiltWorker classes
 * (NoSuchMethodException — they need @AssistedInject, not a plain (Context, Params) ctor).
 *
 * These UI tests do not test WorkManager logic; they test snackbar / state persistence.
 * A no-op WorkerFactory is therefore the correct solution: it returns a Worker that
 * immediately succeeds, preventing the reflection fallback without pulling in HiltWorkerFactory
 * (which requires fragile EntryPoint bridge-code generation from @CustomTestApplication).
 */
open class WorkerTestApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(NoOpWorkerFactory)
            .build()
}

private object NoOpWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker = object : Worker(appContext, workerParameters) {
        override fun doWork(): Result = Result.success()
    }
}
