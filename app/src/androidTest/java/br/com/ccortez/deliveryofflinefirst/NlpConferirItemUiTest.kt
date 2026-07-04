package br.com.ccortez.deliveryofflinefirst

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
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
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * Instrumented end-to-end tests for the `CONFERIR_ITEM` NLP command.
 *
 * ## Architecture under test
 * The full stack is exercised: the user's typed phrase travels from the Compose UI to
 * the ViewModel, which dispatches it to [FakeNlpRepository] (no real Gemini call), then
 * executes the CONFERIR_ITEM branch (global search, ambiguity detection, or contextual
 * resolution) before writing to Room and emitting a one-shot Snackbar event.
 *
 * ## @BindValue for per-test NLP configuration
 * The [fakeNlpRepository] field is annotated with `@BindValue`, which causes Hilt to
 * use this instance as the `NlpRepository` binding without declaring it inside the
 * inner `@Module`. This allows each `@Test` to reconfigure [FakeNlpRepository.response]
 * before the interaction, simulating different Orchestrator-Worker pipeline outcomes.
 *
 * The inner [TestModule] provides everything else that [UninstallModules] removed from
 * [AppModule]: an in-memory Room database (clean state per process start), DataStore,
 * [FakeRemoteConfigRepository] (nlp_enabled = true so the Send button is visible),
 * and [FakeAnalyticsRepository] (no-op).
 *
 * ## Snackbar assertions
 * The Snackbar is rendered by Material 3's `SnackbarHost` and its text node is
 * accessible in the semantic tree while visible. `waitUntil` polls until the node
 * appears — accommodating the coroutine round-trip (NLP fake response + Room write
 * + SharedFlow emission + LaunchedEffect collection + Compose recomposition).
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
class NlpConferirItemUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ── @BindValue: NlpRepository provided per-test-class, configurable per-method ──

    /**
     * Hilt injects this field as the [NlpRepository] binding.
     * Cast to [FakeNlpRepository] in each test to set the desired pipeline response
     * before the user interaction triggers `EntregasViewModel.processarComandoNLP`.
     */
    @BindValue
    @JvmField
    val fakeNlpRepository: NlpRepository = FakeNlpRepository()

    // ── Test module — replaces AppModule for this test class ──────────────────

    /**
     * Provides all bindings that [AppModule] would have provided, EXCEPT [NlpRepository]
     * which is supplied by the [@BindValue][BindValue] field above.
     *
     * In-memory Room: schema is always at version 4 (current), no migration path needed,
     * no data leaks between test runs.
     */
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

        /** nlp_enabled = true keeps the Send button rendered in EntregasScreen. */
        @Provides @Singleton
        fun provideRemoteConfigRepository(): RemoteConfigRepository =
            FakeRemoteConfigRepository(nlpEnabled = true)

        @Provides @Singleton
        fun provideAnalyticsRepository(): AnalyticsRepository = FakeAnalyticsRepository()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Waits for the ViewModel's seed coroutine to finish and the LazyColumn to render
     * at least "Ana Paula Ferreira" before any test interaction begins.
     */
    private fun aguardarSeedData() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Ana Paula Ferreira")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * Types [texto] in the search field and taps the Send button to trigger
     * `EntregasViewModel.processarComandoNLP`.
     * The text is irrelevant to the outcome — [fakeNlpRepository] determines the
     * [NlpCommand] returned, not the actual content typed.
     */
    private fun enviarComando(texto: String) {
        composeTestRule.onNodeWithTag("campo_busca").performTextInput(texto)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Enviar comando para IA")
            .performClick()
    }

    // ── Cenário A — Sucesso Global Sem Ambiguidade ────────────────────────────

    /**
     * FakeNlpRepository returns CONFERIR_ITEM with targetItem = "mochila" (no targetClient).
     * "Mochila Escolar Estampada" is unique across all deliveries (only in Ana Paula's list).
     * Expected result: item is checked and a success Snackbar is shown.
     */
    @Test
    fun cenarioA_globalSemAmbiguidade_exibeSnackbarDeSucesso() {
        (fakeNlpRepository as FakeNlpRepository).response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "mochila",
            itemConferidoState = true
        )

        aguardarSeedData()
        enviarComando("conferei a mochila")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("'Mochila Escolar Estampada' conferido.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onAllNodesWithText("'Mochila Escolar Estampada' conferido.")
            .fetchSemanticsNodes()
            .let { nodes ->
                assert(nodes.isNotEmpty()) {
                    "Snackbar de sucesso esperado para match único de 'mochila'"
                }
            }
    }

    // ── Cenário B — Fallback de Ambiguidade Protetiva ─────────────────────────

    /**
     * FakeNlpRepository returns CONFERIR_ITEM with targetItem = "caixa de papelão"
     * and NO targetClient. "Caixa de Papelão 50×40×30 cm" exists in two deliveries
     * (Ana Paula and Carlos Lima) — the ViewModel must block the operation and warn the driver.
     *
     * Expected result: no Room write occurs and a Snackbar with the ambiguity message appears.
     */
    @Test
    fun cenarioB_ambiguidadeProtetiva_exibeSnackbarDeAlerta() {
        (fakeNlpRepository as FakeNlpRepository).response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "caixa de papelão",
            itemConferidoState = true
            // targetClient omitido — busca global, ambígua
        )

        aguardarSeedData()
        enviarComando("conferei a caixa de papelão")

        // The ambiguity guard emits the multi-match warning
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(
                    "Existe mais de um item correspondente a 'caixa de papelão'." +
                    " Por favor, informe o nome do cliente.",
                    substring = true
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onAllNodesWithText("Existe mais de um item", substring = true)
            .fetchSemanticsNodes()
            .let { nodes ->
                assert(nodes.isNotEmpty()) {
                    "Snackbar de alerta de ambiguidade não encontrado"
                }
            }
    }

    // ── Cenário C — Resolução de Ambiguidade por Contexto ─────────────────────

    /**
     * FakeNlpRepository returns CONFERIR_ITEM with targetItem = "caixa de papelão"
     * AND targetClient = "Ana Paula". The ViewModel scopes the search to Ana Paula's
     * delivery only → seed-1-d matches uniquely.
     *
     * Expected result: success Snackbar for Ana Paula's "Caixa de Papelão 50×40×30 cm".
     */
    @Test
    fun cenarioC_resolucaoContextual_exibeSnackbarDeSucessoParaClienteEspecifico() {
        (fakeNlpRepository as FakeNlpRepository).response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "caixa de papelão",
            targetClient       = "Ana Paula",
            itemConferidoState = true
        )

        aguardarSeedData()
        enviarComando("conferei a caixa de papelão da Ana Paula")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("'Caixa de Papelão 50×40×30 cm' conferido.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onAllNodesWithText("'Caixa de Papelão 50×40×30 cm' conferido.")
            .fetchSemanticsNodes()
            .let { nodes ->
                assert(nodes.isNotEmpty()) {
                    "Snackbar de sucesso esperado para caixa de papelão da Ana Paula"
                }
            }
    }
}
