package br.com.ccortez.deliveryofflinefirst

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
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

/**
 * Instrumented tests that verify the end-to-end text-search behaviour of `EntregasScreen`.
 *
 * ## Why [UninstallModules]
 * The production [AppModule] wires Firebase Remote Config, Firebase AI (Gemini), and a
 * file-backed Room database. All three are inappropriate for automated tests:
 *  - Firebase services require a live network and valid API keys.
 *  - The on-device Room file persists between runs, making tests order-dependent.
 *
 * [UninstallModules] removes [AppModule] from the Hilt graph for this test class;
 * the inner [TestModule] replaces every binding with deterministic alternatives:
 *  - `Room.inMemoryDatabaseBuilder` → fresh schema on every process start, no migrations needed.
 *  - [FakeRemoteConfigRepository] → `nlp_enabled = true` (default, not under test here).
 *  - [FakeNlpRepository] → no Gemini call, returns UNKNOWN immediately.
 *  - [FakeAnalyticsRepository] → no-op, no events sent to Firebase.
 *
 * ## What is tested
 * The `EntregasViewModel.entregasFiltradas` pipeline:
 *   `_searchQuery → debounce(300ms) → distinctUntilChanged → flatMapLatest → filter`
 * is exercised through the real Compose UI: typing in `campo_busca` propagates the query
 * through the ViewModel to Room and back, proving the full reactive chain works in a
 * real Android process — something no unit test can replicate.
 *
 * The auto-expand behaviour of `EntregaCard` (item cards open automatically when the
 * search query matches an item name) is also covered because it involves both the ViewModel
 * state and a Compose `remember(searchQuery, itens)` derivation.
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
class BuscaTextualTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ── Test Hilt module ───────────────────────────────────────────────────────

    /**
     * Replaces [AppModule] with deterministic, test-friendly dependencies.
     *
     * Hilt sees this inner object as an additional module for the test component,
     * combined with [UninstallModules] removing the production module. Every binding
     * that `EntregasViewModel` and `SettingsViewModel` need must be provided here;
     * a missing binding would cause a Hilt compile-time error.
     */
    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {

        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()

        @Provides
        fun provideEntregaDao(db: AppDatabase): EntregaDao = db.entregaDao()

        @Provides
        @Singleton
        fun provideEntregaRepository(dao: EntregaDao): EntregaRepository =
            EntregaRepositoryImpl(dao)

        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext ctx: Context): DataStore<SettingsConfig> =
            ctx.settingsDataStore

        @Provides
        @Singleton
        fun provideSettingsRepository(
            dataStore: DataStore<SettingsConfig>
        ): SettingsRepository = SettingsRepositoryImpl(dataStore)

        /**
         * NLP is not under test here — any command typed returns UNKNOWN immediately.
         * This prevents the test from ever reaching the real Gemini API.
         */
        @Provides
        @Singleton
        fun provideNlpRepository(): NlpRepository = FakeNlpRepository()

        /**
         * Remote Config returns `nlp_enabled = true` (the default).
         * This keeps the Send button visible so its presence doesn't interfere
         * with text-search assertions.
         */
        @Provides
        @Singleton
        fun provideRemoteConfigRepository(): RemoteConfigRepository =
            FakeRemoteConfigRepository(nlpEnabled = true)

        @Provides
        @Singleton
        fun provideAnalyticsRepository(): AnalyticsRepository = FakeAnalyticsRepository()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Waits until `EntregasViewModel.popularBancoSeVazio` has seeded the in-memory Room DB
     * and the Compose UI has rendered the first delivery ("Ana Paula Ferreira").
     *
     * The seed runs as a coroutine inside the ViewModel's `init` block. Using `waitUntil`
     * instead of a fixed `Thread.sleep` avoids both false-positives (test passing before
     * data arrives) and wasted time (seed usually finishes in < 200 ms).
     */
    private fun aguardarSeedData() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Ana Paula Ferreira")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * Clearing the search field after a previous filter must restore the full delivery list.
     *
     * Verifies that `EntregasViewModel.onSearchQueryChange` with an empty string
     * causes `entregasFiltradas` to emit the complete list again.
     */
    @Test
    fun busca_vazia_exibe_todas_as_entregas() {
        aguardarSeedData()

        composeTestRule.onNodeWithTag("campo_busca").performTextInput("Carlos")
        composeTestRule.waitUntil(2_000) {
            composeTestRule.onAllNodesWithText("Ana Paula Ferreira").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithTag("campo_busca").performTextClearance()

        // After clearing, all seed deliveries must reappear.
        // Scroll to "João Silva" (3rd card) before asserting — LazyColumn only renders
        // visible items, so cards that are off-screen are not in the composition tree.
        aguardarSeedData()
        composeTestRule.onAllNodesWithText("Carlos Lima").assertCountEquals(1)
        composeTestRule.onNodeWithTag("lista_entregas").performScrollToNode(hasText("João Silva"))
        composeTestRule.onAllNodesWithText("João Silva").assertCountEquals(1)
    }

    /**
     * Typing a client name must hide all deliveries that do not match.
     *
     * Pipeline: `_searchQuery("Carlos") → debounce → flatMapLatest → filter(cliente.contains)`
     * → Room emits new list → Compose recomposition shows only "Carlos Lima".
     */
    @Test
    fun busca_por_nome_de_cliente_filtra_a_lista() {
        aguardarSeedData()

        composeTestRule.onNodeWithTag("campo_busca").performTextInput("Carlos")

        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodesWithText("Ana Paula Ferreira").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onAllNodesWithText("Carlos Lima").assertCountEquals(1)
    }

    /**
     * Typing part of an address must return deliveries whose `endereco` contains the term.
     *
     * Roberto Alves is the only delivery with "Alameda Santos" in its address.
     */
    @Test
    fun busca_por_endereco_filtra_a_lista() {
        aguardarSeedData()

        composeTestRule.onNodeWithTag("campo_busca").performTextInput("Alameda Santos")

        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodesWithText("Ana Paula Ferreira").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onAllNodesWithText("Roberto Alves").assertCountEquals(1)
    }

    /**
     * Typing a product name must return the delivery that contains that item — even when
     * the client name and address do not match the query.
     *
     * "Notebook Dell" is an item under Carlos Lima; neither "Carlos Lima" nor his address
     * contains the word "Notebook". This verifies that the third branch of the predicate
     * (`itens.any { it.nome.contains(...) }`) is exercised end-to-end.
     */
    @Test
    fun busca_por_nome_de_item_filtra_a_lista() {
        aguardarSeedData()

        composeTestRule.onNodeWithTag("campo_busca").performTextInput("Notebook Dell")

        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodesWithText("Ana Paula Ferreira").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onAllNodesWithText("Carlos Lima").assertCountEquals(1)
    }

    /**
     * When the search query matches an item name, `EntregaCard` must auto-expand
     * to surface the matching item without a manual tap.
     *
     * This validates the `remember(searchQuery, itens)` derivation in `EntregaCard`:
     *   `shouldExpand = query.isNotBlank() && itens.any { it.nome.contains(query) }`
     *
     * "Notebook Dell XPS 15" is the item under Carlos Lima. After typing "Notebook",
     * the card for Carlos Lima should already be expanded and the item text visible.
     */
    @Test
    fun busca_por_nome_de_item_expande_card_automaticamente() {
        aguardarSeedData()

        composeTestRule.onNodeWithTag("campo_busca").performTextInput("Notebook")

        // Wait for filter to resolve
        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodesWithText("Ana Paula Ferreira").fetchSemanticsNodes().isEmpty()
        }

        // The item text is only rendered when the card is expanded
        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule
                .onAllNodesWithText("Notebook Dell XPS 15 (i7 / 32GB)")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
