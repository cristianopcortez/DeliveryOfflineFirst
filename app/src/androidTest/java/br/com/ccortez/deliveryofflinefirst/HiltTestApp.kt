package br.com.ccortez.deliveryofflinefirst

import dagger.hilt.android.testing.CustomTestApplication

/**
 * Triggers Hilt's code generation for the test Application class.
 *
 * Hilt generates HiltTestApp_Application, which:
 *   - extends WorkerTestApplication (so Configuration.Provider is satisfied)
 *   - adds the @HiltAndroidApp machinery needed for test components
 *   - injects WorkerTestApplication's @Inject fields (including workerFactory)
 *
 * HiltTestRunner references the generated class by name so the Android test
 * framework uses it instead of the default HiltTestApplication.
 */
@Suppress("unused") // processed by KSP — generates HiltTestApp_Application used by HiltTestRunner
@CustomTestApplication(WorkerTestApplication::class)
interface HiltTestApp
