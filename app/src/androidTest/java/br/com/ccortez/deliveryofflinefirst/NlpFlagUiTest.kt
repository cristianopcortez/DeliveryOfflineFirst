package br.com.ccortez.deliveryofflinefirst

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.SettingsConfig
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.settingsDataStore
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.SettingsRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.di.AppModule
import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.SettingsRepository
import br.com.ccortez.deliveryofflinefirst.fake.FakeAnalyticsRepository
import br.com.ccortez.deliveryofflinefirst.fake.FakeNlpRepository
import br.com.ccortez.deliveryofflinefirst.fake.FakeRemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════════════════════
//  NLP HABILITADO  (nlp_enabled = true)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Verifies the UI behaviour of `EntregasScreen` when the Remote Config flag
 * `nlp_enabled` is **true**.
 *
 * ## What is under test
 * `EntregasViewModel.fetchRemoteConfig` calls [RemoteConfigRepository.isNlpEnabled]
 * on `init`. The returned value propagates via `_nlpEnabled: MutableStateFlow<Boolean>`
 * through `AppNavigation` and into `EntregasScreen` as the `nlpEnabled` parameter.
 * The screen conditionally renders:
 *  - A trailing Send icon button (`contentDescription = "Enviar comando para IA"`)
 *  - A placeholder that describes AI usage
 * These are the observable effects this test class asserts.
 *
 * ## Why [UninstallModules]
 * The fake [RemoteConfigRepository] must return `true` *synchronously* — before the
 * ViewModel's coroutine completes — to avoid a race where the UI briefly shows the
 * `false` branch (optimistic default = true, so actually no race here, but the module
 * replacement also removes the real Firebase dependency from the test process).
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
class NlpHabilitadoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {

        @Provides @Singleton
        fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()

        @Provides
        fun provideEntregaDao(db: AppDatabase): EntregaDao = db.entregaDao()

        @Provides @Singleton
        fun provideEntregaRepository(dao: EntregaDao): EntregaRepository =
            EntregaRepositoryImpl(dao)

        @Provides @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext ctx: Context
        ): DataStore<SettingsConfig> = ctx.settingsDataStore

        @Provides @Singleton
        fun provideSettingsRepository(
            dataStore: DataStore<SettingsConfig>
        ): SettingsRepository = SettingsRepositoryImpl(dataStore)

        @Provides @Singleton
        fun provideNlpRepository(): NlpRepository = FakeNlpRepository()

        /** Returns `true` — NLP feature enabled. */
        @Provides @Singleton
        fun provideRemoteConfigRepository(): RemoteConfigRepository =
            FakeRemoteConfigRepository(nlpEnabled = true)

        @Provides @Singleton
        fun provideAnalyticsRepository(): AnalyticsRepository = FakeAnalyticsRepository()
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * When `nlp_enabled = true`, `EntregasScreen` must show a Send button so the user
     * can submit a natural-language command to Gemini.
     *
     * The button is rendered as an `IconButton` with `contentDescription = "Enviar comando para IA"`.
     * It is absent from the Compose tree when `nlpEnabled = false` — so this assertion proves
     * that the correct branch of the conditional `trailingIcon` expression was evaluated.
     *
     * `waitUntil` accounts for the ViewModel's `init` coroutine: `_nlpEnabled` starts as
     * `true` (optimistic), is then confirmed by [FakeRemoteConfigRepository]. The button
     * is visible from the very first frame, but `waitUntil` makes the intent explicit.
     */
    @Test
    fun nlp_habilitado_exibe_botao_enviar() {
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Enviar comando para IA")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onAllNodesWithContentDescription("Enviar comando para IA")
            .fetchSemanticsNodes()
            .let { nodes -> assert(nodes.isNotEmpty()) { "Botão Enviar não encontrado com nlp_enabled=true" } }
    }

    /**
     * When `nlp_enabled = true`, the search field placeholder must describe the AI
     * capability: "Buscar ou descreva um comando de IA…".
     *
     * The placeholder is rendered as a `Text` composable inside `OutlinedTextField` when
     * `searchQuery` is empty (the initial state). It is included in the semantic tree
     * and therefore accessible via `onNodeWithText`.
     */
    @Test
    fun nlp_habilitado_exibe_placeholder_com_descricao_de_ia() {
        composeTestRule
            .onNodeWithText("Buscar ou descreva um comando de IA...")
            .assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  NLP DESABILITADO  (nlp_enabled = false)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Verifies the UI behaviour of `EntregasScreen` when the Remote Config flag
 * `nlp_enabled` is **false**.
 *
 * ## Optimistic-default race
 * `EntregasViewModel` initialises `_nlpEnabled = MutableStateFlow(true)` so the UI
 * never flickers on startup. When [FakeRemoteConfigRepository] returns `false`,
 * `fetchRemoteConfig()` overwrites the value. The race window is tiny in tests
 * (the coroutine runs immediately), but `waitUntil` waits for the final state
 * before asserting — making the test robust regardless of coroutine scheduling.
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
class NlpDesabilitadoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {

        @Provides @Singleton
        fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()

        @Provides
        fun provideEntregaDao(db: AppDatabase): EntregaDao = db.entregaDao()

        @Provides @Singleton
        fun provideEntregaRepository(dao: EntregaDao): EntregaRepository =
            EntregaRepositoryImpl(dao)

        @Provides @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext ctx: Context
        ): DataStore<SettingsConfig> = ctx.settingsDataStore

        @Provides @Singleton
        fun provideSettingsRepository(
            dataStore: DataStore<SettingsConfig>
        ): SettingsRepository = SettingsRepositoryImpl(dataStore)

        @Provides @Singleton
        fun provideNlpRepository(): NlpRepository = FakeNlpRepository()

        /** Returns `false` — NLP feature disabled. */
        @Provides @Singleton
        fun provideRemoteConfigRepository(): RemoteConfigRepository =
            FakeRemoteConfigRepository(nlpEnabled = false)

        @Provides @Singleton
        fun provideAnalyticsRepository(): AnalyticsRepository = FakeAnalyticsRepository()
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * When `nlp_enabled = false`, the Send button must be absent from the Compose tree.
     *
     * In `EntregasScreen` the `trailingIcon` parameter of `OutlinedTextField` is set to
     * `null` when `nlpEnabled = false`, so the `IconButton` is never composed — not just
     * invisible, but completely removed from the UI hierarchy.
     *
     * `waitUntil` waits for the optimistic `true` to be replaced by `false` before
     * asserting, preventing a false-positive caused by the initial default value.
     */
    @Test
    fun nlp_desabilitado_oculta_botao_enviar() {
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Enviar comando para IA")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        composeTestRule
            .onAllNodesWithContentDescription("Enviar comando para IA")
            .fetchSemanticsNodes()
            .let { nodes -> assert(nodes.isEmpty()) { "Botão Enviar encontrado com nlp_enabled=false" } }
    }

    /**
     * When `nlp_enabled = false`, the search field placeholder must show a plain-text
     * hint with no mention of AI: "Buscar entrega ou item…".
     *
     * This is the simpler UX for users in environments where the AI flag is off —
     * they see only the offline-first text search, with no AI affordance in the UI.
     */
    @Test
    fun nlp_desabilitado_exibe_placeholder_simples() {
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Enviar comando para IA")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        composeTestRule
            .onNodeWithText("Buscar entrega ou item...")
            .assertIsDisplayed()
    }
}
