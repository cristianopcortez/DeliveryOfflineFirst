package br.com.ccortez.deliveryofflinefirst.di

import android.content.Context
import androidx.datastore.core.DataStore
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.SettingsConfig
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.settingsDataStore
import br.com.ccortez.deliveryofflinefirst.data.repository.AnalyticsRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.NlpRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.RemoteConfigRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.SettingsRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpPrompts
import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import br.com.ccortez.deliveryofflinefirst.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideEntregaDao(db: AppDatabase): EntregaDao =
        db.entregaDao()

    @Provides
    @Singleton
    fun provideEntregaRepository(dao: EntregaDao): EntregaRepository =
        EntregaRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<SettingsConfig> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<SettingsConfig>): SettingsRepository =
        SettingsRepositoryImpl(dataStore)

    /**
     * Creates the Gemini model instance once for the entire app lifetime.
     *
     * - backend = googleAI(): uses the Gemini Developer API via Firebase.
     *   Switch to GenerativeBackend.vertexAI() if you move to a Vertex AI project.
     * - responseMimeType = "application/json": instructs the model to produce
     *   constrained JSON output (no markdown, no prose).
     * - systemInstruction: loaded from NlpPrompts so the model always acts as a
     *   strict parser, regardless of which screen triggers the call.
     */
    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content {
                text(NlpPrompts.DELIVERY_ASSISTANT_SYSTEM_PROMPT)
            }
        )

    @Provides
    @Singleton
    fun provideNlpRepository(model: GenerativeModel): NlpRepository =
        NlpRepositoryImpl(model)

    /**
     * Provides a FirebaseRemoteConfig instance with in-app defaults.
     *
     * Default nlp_enabled = true: the feature stays on until Firebase explicitly disables it.
     * minimumFetchIntervalInSeconds = 3600: allows at most one fresh fetch per hour in production.
     * In debug builds you can lower this to 0 for fast iteration without affecting the release quota.
     */
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig =
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    // Option A: bypass the 12-hour cache in debug so every fetchAndActivate()
                    // goes to the server immediately — no need to kill the app to see changes.
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
                }
            )
            setDefaultsAsync(mapOf(RemoteConfigRepositoryImpl.KEY_NLP_ENABLED to true))
        }

    @Provides
    @Singleton
    fun provideRemoteConfigRepository(remoteConfig: FirebaseRemoteConfig): RemoteConfigRepository =
        RemoteConfigRepositoryImpl(remoteConfig)

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(analytics: FirebaseAnalytics): AnalyticsRepository =
        AnalyticsRepositoryImpl(analytics)
}
