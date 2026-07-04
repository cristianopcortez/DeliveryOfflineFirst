# Delivery Offline First

Android offline-first delivery app built with Jetpack Compose, MVVM + Clean Architecture, Room, StateFlow and WorkManager.

> Built as a focused portfolio project to demonstrate senior-level Android patterns in a realistic field-delivery context: a driver app that works without network connectivity and synchronizes data when the connection is restored.

---

## Architecture

```
deliveryofflinefirst/
├── data/
│   ├── local/
│   │   ├── datastore/  # SettingsConfig, SettingsSerializer, AppSettingsDataStore
│   │   └── (room)      # EntregaEntity, ItemPedidoEntity, EntregaComProdutosEntity
│   │                   # EntregaDao, AppDatabase (v4)
│   ├── repository/     # EntregaRepositoryImpl, NlpRepositoryImpl, SettingsRepositoryImpl, RemoteConfigRepositoryImpl, AnalyticsRepositoryImpl
│   └── worker/         # SyncWorker (CoroutineWorker via Hilt)
├── di/                 # AppModule — Hilt SingletonComponent
├── domain/
│   ├── model/          # Entrega, ItemPedido, EntregaComProdutos (pure Kotlin, no Android deps)
│   ├── nlp/            # NlpAction, NlpCommand, NlpPrompts (Gemini system instructions)
│   └── repository/     # EntregaRepository, NlpRepository, SettingsRepository, RemoteConfigRepository, AnalyticsRepository interfaces
├── navigation/         # AppNavigation — NavHost with Entregas + Settings routes
└── presentation/
    ├── screen/         # EntregasScreen, SettingsScreen (stateless composables)
    └── viewmodel/      # EntregasViewModel, SettingsViewModel + UiState/Event types
```

**Pattern:** Clean Architecture + MVVM  
**DI:** Hilt (`@HiltViewModel`, `@HiltWorker`, `@Singleton` module)  
**Single source of truth:** Room — the UI never talks to the network directly

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| State | StateFlow + SharedFlow |
| DI | Hilt |
| Local DB | Room + KSP |
| Background sync | WorkManager |
| Architecture | Clean Architecture + MVVM |
| Language | Kotlin 2.0 |
| Feature flags | Firebase Remote Config |
| AI assistant | Firebase AI Logic (Gemini 2.5 Flash) |
| Adoption tracking | Firebase Analytics |

---

## Jetpack Compose & State Management

### Immutable UiState + ViewModel as source of truth

`EntregasUiState` is a `data class` with only `val` properties. The ViewModel holds a private `MutableStateFlow` and exposes a read-only `StateFlow` via `asStateFlow()`. The UI never mutates state directly.

```kotlin
// EntregasUiState.kt
data class EntregasUiState(
    val isLoading: Boolean = false,
    val entregas: List<Entrega> = emptyList(),
    val erro: String? = null
)

// EntregasViewModel.kt
private val _uiState = MutableStateFlow(EntregasUiState())
val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()
```

### `collectAsStateWithLifecycle` — lifecycle-aware collection

The screen stops collecting when it leaves the `STARTED` state, preventing unnecessary work while the app is in the background.

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
val entregasFiltradas by viewModel.entregasFiltradas.collectAsStateWithLifecycle()
val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
```

### `remember` vs `rememberSaveable` vs ViewModel — choosing the right tool

```kotlin
// 1. remember — ephemeral UI state, intentionally reset on rotation
//    Use for: dropdown open/close, animation state, transient flags
var expanded by remember { mutableStateOf(false) }

// 2. rememberSaveable — survives rotation and system-initiated process death
//    (saved to Bundle), but does NOT live in the ViewModel
//    Use for: transient user input that should survive rotation
//    but does not belong to business logic (no need for debounce, Flow, etc.)
var anotacaoRapida by rememberSaveable { mutableStateOf("") }

// 3. ViewModel StateFlow — survives rotation via ViewModel lifecycle
//    Better than rememberSaveable for search/filter: no Bundle size limit,
//    enables debounce + distinctUntilChanged + flatMapLatest
val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
```

| Mechanism | Survives rotation | Survives process death | Scope |
|---|---|---|---|
| `remember` | ✗ | ✗ | Composition only |
| `rememberSaveable` | ✓ | ✓ (Bundle) | Composition + saved state |
| ViewModel `StateFlow` | ✓ | ✗ | ViewModel scope |

> **Rule of thumb:** use `rememberSaveable` for UI-only transient input (e.g. a quick note field). Move state to the ViewModel only when it needs operators (debounce, combine, flatMapLatest) or when the value is shared across composables.

### `derivedStateOf` — avoiding unnecessary recomposition

Two uses in `EntregasScreen`, both following the same principle: the source changes frequently, but the derived value changes rarely.

```kotlin
// Recomposes the sync badge only when the pending count changes,
// not on every list update
val pendentesSync by remember {
    derivedStateOf { state.entregas.count { !it.entrega.sincronizada } }
}

// Scroll position changes on every pixel — derivedStateOf fires only when the boolean flips
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

### State hoisting — stateless composables

All child composables receive state and callbacks as parameters. None of them own state internally.

```kotlin
// ClienteFilterDropdown: does not open/close itself
@Composable
private fun ClienteFilterDropdown(
    selectedCliente: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClienteSelected: (String) -> Unit,
    // ...
)

// EntregaCard: does not know what "conclude" or "check item" means
@Composable
private fun EntregaCard(
    entregaComProdutos: EntregaComProdutos,
    onConcluir: () -> Unit,
    onConferirItem: (itemId: String, conferido: Boolean) -> Unit,
)

// PendenteSyncBadge: only renders, holds no state
@Composable
private fun PendenteSyncBadge(count: Int, modifier: Modifier = Modifier)
```

### Side effects — `LaunchedEffect` and `rememberCoroutineScope`

```kotlin
// LaunchedEffect: collects SharedFlow events tied to the composition lifecycle
// The snackbar does NOT re-appear on rotation (SharedFlow has no replay)
LaunchedEffect(Unit) {
    viewModel.eventos.collect { evento ->
        when (evento) {
            is EntregasEvent.ShowSnackbar -> snackbarHostState.showSnackbar(evento.message)
        }
    }
}

// rememberCoroutineScope: launching a coroutine from a click callback
val coroutineScope = rememberCoroutineScope()
FloatingActionButton(onClick = {
    coroutineScope.launch { listState.animateScrollToItem(0) }
})
```

### `LazyColumn` with stable keys

```kotlin
items(items = entregasExibidas, key = { it.entrega.id }) { ec ->
    EntregaCard(
        entregaComProdutos = ec,
        onConcluir = { viewModel.concluirEntrega(ec.entrega.id) },
        onConferirItem = { itemId, conferido ->
            viewModel.conferirItem(ec.entrega.id, itemId, conferido)
        }
    )
}
```

Without `key`, inserting one item at the top would recompose the entire list. With `key = { it.entrega.id }`, Compose only recomposes the affected card — and the local `expandido` state inside each `EntregaCard` is preserved across scroll and item check recompositions.

### Unidirectional Data Flow (UDF)

```
UI emits event  →  viewModel.concluirEntrega(id)
                         ↓
              repository.concluirEntrega(id)  →  Room updates
                         ↓
              Room Flow emits new list  →  _uiState.update { }
                         ↓
              collectAsStateWithLifecycle  →  screen recomposes
```

The UI only reads state and emits events. The ViewModel is the only one that mutates state.

---

## Coroutines & Flow

### `StateFlow` vs `SharedFlow` — state vs one-shot events

```kotlin
// StateFlow: always has a current value, re-emits to new collectors (rotation safe)
val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

// SharedFlow: no current value, no replay — the snackbar does NOT re-appear on rotation
private val _eventos = MutableSharedFlow<EntregasEvent>()
val eventos = _eventos.asSharedFlow()
```

> **Rule:** `StateFlow` for state (what the screen shows now). `SharedFlow` for one-shot events (navigation, snackbar, toast).

### `combine` + `debounce` + `distinctUntilChanged` + `flatMapLatest` + `stateIn`

The reactive search pipeline in `EntregasViewModel` demonstrates five operators in sequence:

```kotlin
val entregasFiltradas: StateFlow<List<Entrega>> = combine(
    _searchQuery.debounce(300).distinctUntilChanged(), // wait 300ms; skip if unchanged
    _selectedCliente
) { query, cliente -> Pair(query, cliente) }
    .flatMapLatest { (query, cliente) ->               // cancel previous query on new input
        repository.observarTodas().map { list ->
            list
                .filter { if (cliente == "Todos") true else it.cliente == cliente }
                .filter {
                    query.isBlank() ||
                    it.cliente.contains(query, ignoreCase = true) ||
                    it.endereco.contains(query, ignoreCase = true)
                }
        }
    }
    .stateIn(                                          // cold → hot; survives rotation
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

| Operator | Why it's here |
|---|---|
| `combine` | Merges two filter sources (search + client) into a single emission |
| `debounce(300)` | Waits for the user to stop typing before querying |
| `distinctUntilChanged` | Skips the query if the value did not actually change |
| `flatMapLatest` | Cancels the in-flight query when a new filter arrives |
| `stateIn(WhileSubscribed(5000))` | Converts the cold Room Flow to a hot StateFlow; keeps it alive 5s without collectors so rotation doesn't restart the upstream |

### `viewModelScope` — survives rotation

```kotlin
// The coroutine continues running when the user rotates the screen.
// The ViewModel is retained across configuration changes.
// The coroutine is only cancelled in onCleared() — when the user leaves the screen for real.
viewModelScope.launch {
    repository.concluirEntrega(id)
    _eventos.emit(EntregasEvent.ShowSnackbar("..."))
    agendarSync()
}
```

### `catch` — error handling in Flow

```kotlin
repository.observarTodas()
    .catch { e ->
        _uiState.update { it.copy(erro = e.message, isLoading = false) }
    }
    .collect { lista ->
        _uiState.update { it.copy(isLoading = false, entregas = lista) }
    }
```

`catch` only intercepts exceptions from the **upstream** (above it in the chain). It does not catch exceptions thrown inside `collect`.

### `CoroutineWorker` — `SyncWorker`

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: EntregaRepository      // injected via Hilt
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            delay(2_000)                            // simulates network latency
            if (Random.nextFloat() < 0.3f) {
                Result.retry()                      // 30% random failure — WorkManager retries with exponential backoff
            } else {
                repository.marcarTodasSincronizadas()
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

### WorkManager — offline sync with constraints and backoff

```kotlin
private fun agendarSync() {
    val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // only runs with network
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    // KEEP: if a sync is already enqueued, do not duplicate it
    WorkManager.getInstance(context)
        .enqueueUniqueWork("sync_entregas", ExistingWorkPolicy.KEEP, request)
}
```

### Reactive WorkManager status observation

No polling. The UI reacts to WorkManager state changes via Flow:

```kotlin
val syncStatus: StateFlow<WorkInfo.State?> = WorkManager.getInstance(context)
    .getWorkInfosForUniqueWorkFlow("sync_entregas")
    .map { infos -> infos.firstOrNull()?.state }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

The `SyncStatusBanner` in the screen shows `RUNNING`, `ENQUEUED`, `SUCCEEDED`, or `FAILED` in real time.

---

## Offline-first architecture — the outbox pattern

```
UI (Compose)
     ↑ observes Flow
┌─────────────────────┐
│  Room = SOURCE OF   │  ← screen ALWAYS reads from local DB
│       TRUTH         │
└─────────────────────┘
     ↑ writes              ↑ writes on sync success
User actions          WorkManager drains when network is available
     ↓                     (exponential backoff + CONNECTED constraint)
┌─────────────────────┐
│  sincronizada=false │  → pending entries visible via derivedStateOf badge
│  (local outbox)     │
└─────────────────────┘
```

**Flow:** user taps "Conclude" → Room updates instantly (`sincronizada=false`) → Room Flow emits → screen reacts → badge shows pending count → WorkManager schedules sync → when network returns, SyncWorker runs → marks entries as synchronized → badge zeroes.

---

## Local Persistence (DataStore × Room)

### Decision table: what the app implements

| Technology | When to use | Data type | Migration | Implemented in this app |
|---|---|---|---|---|
| **DataStore Preferences** | Simple settings (booleans, loose strings) | Primitives with `Preferences.Key<T>` | No formal migration | — Not used (replaced by the option below) |
| **Typed DataStore** + `kotlinx.serialization` | Structured settings — cohesive, type-safe object | `@Serializable data class` | New fields with `defaultValue` | ✅ `SettingsConfig` — `darkTheme` + `driverName` |
| **Room** | Operational data with queries, filters and relationships | `@Entity` + `@Dao` + SQL | `Migration(from, to)` with explicit SQL | ✅ `EntregaEntity` — list + conclude + sync flag |

**Why Typed DataStore instead of Preferences DataStore?**  
Preferences DataStore uses string keys like `stringPreferencesKey("dark_theme")` — a typo is a silent runtime bug. With a `@Serializable data class`, misspelling a field name is a compile-time error.

**Why not use Room for settings?**  
Room is designed for datasets that need queries (filtering, ordering, JOIN). Persisting two config fields in Room adds SQL overhead with no benefit — DataStore is atomic, coroutine-native, and requires no SQL migrations for simple schema additions.

**Code navigation to demonstrate the table:**

```
1. Typed DataStore
   SettingsConfig.kt            ← @Serializable data class (the "proto")
   SettingsSerializer.kt        ← readFrom / writeTo with kotlinx.serialization
   AppSettingsDataStore.kt      ← by dataStore(fileName = "settings.json")
   SettingsRepositoryImpl.kt    ← dataStore.updateData { it.copy(…) }
   SettingsScreen.kt            ← dark theme switch + driver name field

2. Room
   EntregaEntity.kt             ← @Entity with horarioConclusao (v2 column) + uuid (v3 column)
   ItemPedidoEntity.kt          ← @Entity with @ForeignKey(onDelete = CASCADE) → entrega(id)
   EntregaComProdutosEntity.kt  ← POJO: @Embedded EntregaEntity + @Relation List<ItemPedidoEntity>
   AppDatabase.kt               ← version = 4 + MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4
   EntregaDao.kt                ← @Transaction observarTodas(): Flow<List<EntregaComProdutosEntity>>
   EntregasScreen.kt            ← expandable card + ItemPedidoRow with Checkbox
```

---

## Room Migration (schema versioning without data loss)

### Context

When the `horarioConclusao` (conclusion timestamp) field was added to the delivery model, an explicit Room migration was required. Using `fallbackToDestructiveMigration()` was intentionally avoided — dropping the local database would erase pending deliveries not yet synced to the server, which is unacceptable in an offline-first field app.

### Where it shows in the app

When a driver taps **"Conclude"** on a delivery card, the repository calls `System.currentTimeMillis()` and passes the timestamp to the DAO. The `EntregaCard` then displays **"Concluded at HH:mm"** in muted text below the status.

```
┌─────────────────────────────┐
│  Carlos Lima                │
│  Av. Brasil, 456            │
│  Status: Concluída          │
│  Concluded at 14:32         │  ← horarioConclusao from DB (migration v2)
└─────────────────────────────┘
```

### The migration

```kotlin
// AppDatabase.kt — version bumped from 1 to 2
@Database(entities = [EntregaEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ALTER TABLE preserves all existing rows — existing deliveries keep their data
            // NULL is the default for rows that existed before this migration
            db.execSQL("ALTER TABLE entrega ADD COLUMN horarioConclusao INTEGER")
        }
    }
}
```

```kotlin
// AppModule.kt — migration registered in the builder
Room.databaseBuilder(context, AppDatabase::class.java, "entregas.db")
    .addMigrations(MIGRATION_1_2)   // explicit migration registered — no data loss
    .build()
```

### Repository generates the timestamp — ViewModel stays clean

The `EntregaRepository` interface signature does not expose the timestamp. The repository implementation is the only layer that knows about `System.currentTimeMillis()`, keeping the domain contract and the ViewModel unchanged.

```kotlin
// EntregaRepository.kt (interface — unchanged)
suspend fun concluirEntrega(id: String)

// EntregaRepositoryImpl.kt — generates timestamp internally
override suspend fun concluirEntrega(id: String) {
    dao.concluirEntrega(id, timestamp = System.currentTimeMillis())
}
```

### Key points (section 5.4 of study material)

- `exportSchema = true` — Room exports the schema as a JSON file to `app/schemas/`, which should be committed to Git. This creates a versioned audit trail of all schema changes.
- `Migration(1, 2)` with explicit SQL — predictable, auditable, and testable with `MigrationTestHelper`.
- `fallbackToDestructiveMigration()` is absent by design — it would silently wipe all local data on version mismatch, destroying offline-queued deliveries.

### Schema audit trail — `app/schemas/…/AppDatabase/`

Room generates one JSON per database version when `exportSchema = true`. These files are committed to Git so that every schema change is visible in PR diffs and testable with `MigrationTestHelper`.

| File | DB version | Columns / tables added | Why |
|---|---|---|---|
| `1.json` | 1 | `id`, `cliente`, `endereco`, `status`, `sincronizada` | Initial schema — core delivery fields + offline sync flag |
| `2.json` | 2 | `horarioConclusao INTEGER` (nullable) | Conclusion timestamp — `ALTER TABLE` preserves existing rows; `NULL` for rows created before this migration |
| `3.json` | 3 | `uuid TEXT NOT NULL DEFAULT ''` | Idempotency key for the outbox pattern — empty string default for seed rows; new deliveries always get a `UUID.randomUUID()` from the repository |
| `4.json` | 4 | new table `item_pedido` with FK → `entrega(id)` + index | Item management feature — `CREATE TABLE` + `CREATE INDEX`; FK with `ON DELETE CASCADE` ensures items are removed when a delivery is deleted |

The full `CREATE TABLE` recorded in `3.json` reflects the cumulative result of all three versions:

```sql
CREATE TABLE IF NOT EXISTS `entrega` (
    `id`               TEXT    NOT NULL,
    `cliente`          TEXT    NOT NULL,
    `endereco`         TEXT    NOT NULL,
    `status`           TEXT    NOT NULL,
    `sincronizada`     INTEGER NOT NULL,
    `horarioConclusao` INTEGER,           -- nullable: added in v2
    `uuid`             TEXT    NOT NULL,  -- added in v3, idempotency key
    PRIMARY KEY(`id`)
)
```

> Each JSON also stores an `identityHash` that Room uses at runtime to detect mismatches between the compiled `@Entity` and the on-device database — if they diverge without a registered migration, Room throws `IllegalStateException` instead of silently corrupting data.

---

## Item Management within Deliveries

### Overview

Each delivery (`Entrega`) now carries a list of products (`ItemPedido`) that the driver can check off one by one as they hand over the parcel. The feature was added as a vertical slice through all layers — domain → data → repository → ViewModel → UI — without breaking any existing behaviour.

```
┌────────────────────────────────────────────────┐
│  EntregaCard (expanded)                        │
│  ─────────────────────────────────────────     │
│  Carlos Lima                            ▲      │  ← IconButton toggles expandido (remember)
│  Av. Brasil, 456 — Centro                      │
│  Status: Em rota                               │
│  ────────────────────────────────────────      │
│  ☐  Notebook Dell XPS 15 (i7 / 32GB)   Qtd:1  │  ← ItemPedidoRow: Checkbox + nome + quantidade
│  ☐  Carregador Universal 65W USB-C     Qtd:1  │
│  ☑  Mouse Logitech MX Master 3         Qtd:1  │  ← conferido=true → nome riscado
│  ☐  Teclado Mecânico Keychron K6       Qtd:1  │
│  ────────────────────────────────────────      │
│  [ Concluir ]                                  │
└────────────────────────────────────────────────┘
```

---

### Domain layer — pure Kotlin models

```kotlin
// domain/model/ItemPedido.kt
data class ItemPedido(
    val id: String,
    val nome: String,
    val quantidade: Int,
    val conferido: Boolean       // driver's check — persisted to Room
)

// domain/model/EntregaComProdutos.kt
data class EntregaComProdutos(
    val entrega: Entrega,
    val itens: List<ItemPedido>
)
```

`ItemPedido` has no Android or Room dependency — it is a plain Kotlin value object. `EntregaComProdutos` is the wrapper that flows from the repository through the ViewModel to the UI; no other type crosses layer boundaries.

---

### Data layer — Room entities and POJO

#### `ItemPedidoEntity` — Foreign Key + Cascade

```kotlin
@Entity(
    tableName = "item_pedido",
    foreignKeys = [
        ForeignKey(
            entity = EntregaEntity::class,
            parentColumns = ["id"],
            childColumns = ["entregaId"],
            onDelete = ForeignKey.CASCADE       // items are deleted when the parent delivery is deleted
        )
    ],
    indices = [Index("entregaId")]              // required by Room when a FK column is used in @Relation
)
data class ItemPedidoEntity(
    @PrimaryKey val id: String,
    val entregaId: String,
    val nome: String,
    val quantidade: Int,
    val conferido: Boolean
)
```

**Why `@Index("entregaId")`?** Room issues a warning if a FK column used in a `@Relation` has no index. Without it, every `SELECT * FROM item_pedido WHERE entregaId = ?` performs a full table scan. The index converts that to an O(log n) B-tree lookup.

#### `EntregaComProdutosEntity` — Room POJO with `@Embedded` + `@Relation`

```kotlin
data class EntregaComProdutosEntity(
    @Embedded val entrega: EntregaEntity,
    @Relation(
        parentColumn = "id",        // EntregaEntity.id
        entityColumn = "entregaId"  // ItemPedidoEntity.entregaId
    )
    val itens: List<ItemPedidoEntity>
)
```

This class is **not an `@Entity`** — it is never persisted directly. Room uses it as a query result type: when the DAO method is annotated with `@Transaction`, Room internally executes `SELECT * FROM entrega` and then `SELECT * FROM item_pedido WHERE entregaId IN (…)`, assembling the result into `EntregaComProdutosEntity` automatically.

> **`@Embedded` vs `@Relation`:**  
> `@Embedded` flattens a nested object into the same row (all fields become columns of the outer SELECT).  
> `@Relation` is a one-to-many relationship resolved by a second query — Room handles the JOIN for you.

---

### DAO — `@Transaction` is mandatory with `@Relation`

```kotlin
@Dao
interface EntregaDao {

    @Transaction                // without this, Room may read entrega and items in two separate
    @Query("SELECT * FROM entrega ORDER BY cliente ASC")   // transactions → inconsistent snapshot
    fun observarTodas(): Flow<List<EntregaComProdutosEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)   // IGNORE on both: no CASCADE is triggered
    suspend fun inserirTodas(entregas: List<EntregaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirItens(itens: List<ItemPedidoEntity>)

    @Query("UPDATE item_pedido SET conferido = :conferido WHERE id = :itemId")
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)

    @Query("SELECT COUNT(*) FROM entrega")
    suspend fun contarEntregas(): Int     // guard for the idempotent seed
}
```

**Why `OnConflictStrategy.IGNORE` instead of `REPLACE`?**

`REPLACE` is shorthand for `DELETE + INSERT`. When the parent row (`entrega`) is deleted, SQLite fires the `ON DELETE CASCADE`, removing all associated `item_pedido` rows. If the app reseeds deliveries with `REPLACE` on every launch, it silently erases all `conferido = true` states the driver set during the session. `IGNORE` skips the insert if the row already exists — the existing data is never touched.

---

### Room Migration 3 → 4

```kotlin
// AppDatabase.kt — version bumped from 3 to 4
@Database(
    entities = [EntregaEntity::class, ItemPedidoEntity::class],
    version = 4,
    exportSchema = true
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `item_pedido` (
                `id`         TEXT    NOT NULL,
                `entregaId`  TEXT    NOT NULL,
                `nome`       TEXT    NOT NULL,
                `quantidade` INTEGER NOT NULL,
                `conferido`  INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`entregaId`) REFERENCES `entrega`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_item_pedido_entregaId` ON `item_pedido` (`entregaId`)"
        )
    }
}
```

`fallbackToDestructiveMigration()` is absent by design — existing delivery rows (including those in the outbox with `sincronizada = false`) are fully preserved. The migration only adds a new table; all existing data is untouched.

---

### Repository — updated interface and implementation

```kotlin
// EntregaRepository.kt — contract changes
interface EntregaRepository {
    fun observarTodas(): Flow<List<EntregaComProdutos>>          // was Flow<List<Entrega>>
    suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>)  // new
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)     // new
    suspend fun contarEntregas(): Int                                       // new (seed guard)
    // … existing methods unchanged
}
```

```kotlin
// EntregaRepositoryImpl.kt — mapping layer
override fun observarTodas(): Flow<List<EntregaComProdutos>> =
    dao.observarTodas().map { list ->
        list.map { entity ->
            EntregaComProdutos(
                entrega = entity.entrega.toEntrega(),
                itens = entity.itens.map { it.toItemPedido() }
            )
        }
    }
```

The mapper keeps `entregaId` (an infrastructure field) out of the domain model. `ItemPedido` only carries `id`, `nome`, `quantidade`, and `conferido` — the association to a delivery is implicit in `EntregaComProdutos`.

---

### ViewModel — extended reactive pipeline and UDF event

#### Filter pipeline now covers item names

```kotlin
// Before: filter only on entrega.cliente and entrega.endereco
// After: also matches any item name within the delivery

val entregasFiltradas: StateFlow<List<EntregaComProdutos>> = _searchQuery
    .debounce(300)
    .distinctUntilChanged()
    .flatMapLatest { query ->
        repository.observarTodas().map { list ->
            list.filter { ec ->
                query.isBlank() ||
                ec.entrega.cliente.contains(query, ignoreCase = true) ||
                ec.entrega.endereco.contains(query, ignoreCase = true) ||
                ec.itens.any { it.nome.contains(query, ignoreCase = true) }  // ← new
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Searching for `"Dell"` now surfaces the Carlos Lima delivery even if `"Dell"` appears nowhere in the client name or address — it matches `Notebook Dell XPS 15` in his item list.

#### `conferirItem` — clean UDF event

```kotlin
fun conferirItem(entregaId: String, itemId: String, conferido: Boolean) {
    viewModelScope.launch {
        repository.atualizarConferido(itemId, conferido)
        // Room emits a new Flow snapshot → entregasFiltradas + uiState react automatically
        // No manual _uiState.update needed — the @Transaction query handles it
    }
}
```

The ViewModel does not manually update `_uiState` after a check — it simply writes to Room and lets the existing `observarTodas()` Flow propagate the change upward. This is the canonical UDF pattern: a single write produces a single reactive emission that the entire screen subscribes to.

#### Idempotent seed with guard

```kotlin
private fun popularBancoSeVazio() {
    viewModelScope.launch {
        if (repository.contarEntregas() > 0) return@launch   // guard: only seeds on first install

        repository.inserirTodas(entregasSeed)
        itensSeed.forEach { (entregaId, itens) ->
            repository.inserirItens(entregaId, itens)
        }
    }
}
```

Without the guard, every cold start would call `inserirTodas` with `REPLACE` — which as explained above would cascade-delete all items and then re-insert them with `conferido = false`, discarding the driver's work. The `contarEntregas()` guard makes the seed a **one-time operation** that runs only on first install.

---

### UI — expandable card with `AnimatedVisibility`

#### State hoisting for the check event

```kotlin
// EntregasScreen.kt — LazyColumn
items(items = entregasExibidas, key = { it.entrega.id }) { ec ->
    EntregaCard(
        entregaComProdutos = ec,
        onConcluir = { viewModel.concluirEntrega(ec.entrega.id) },
        onConferirItem = { itemId, conferido ->
            viewModel.conferirItem(ec.entrega.id, itemId, conferido)  // UDF event
        }
    )
}
```

`EntregaCard` and `ItemPedidoRow` are stateless: they receive data and callbacks, never owning what they display. The check event travels up via `onConferirItem` → `viewModel.conferirItem` → Room → Flow → recomposition.

#### Expand/collapse with local `remember`

```kotlin
@Composable
private fun EntregaCard(
    entregaComProdutos: EntregaComProdutos,
    onConcluir: () -> Unit,
    onConferirItem: (itemId: String, conferido: Boolean) -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }   // local — resets on navigation, not on item check

    AnimatedVisibility(visible = expandido) {
        Column {
            HorizontalDivider()
            entregaComProdutos.itens.forEach { item ->
                ItemPedidoRow(item = item, onConferido = { onConferirItem(item.id, it) })
            }
        }
    }
}
```

`expandido` uses `remember` (not `rememberSaveable`) intentionally — the expanded state is ephemeral UI state that does not need to survive process death or navigation. The stable `key = { it.entrega.id }` in `LazyColumn` ensures the composable for each card is not recreated during scroll or item updates, preserving `expandido` across recompositions triggered by a Checkbox check.

> **Why not `rememberSaveable` here?** Restoring every card's expand state across rotation or process death adds Bundle overhead with no user benefit — the list is short and re-expanding takes a single tap.

#### Visual feedback — strikethrough on confirmed items

```kotlin
@Composable
private fun ItemPedidoRow(item: ItemPedido, onConferido: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.conferido, onCheckedChange = onConferido)
        Column {
            Text(
                text = item.nome,
                textDecoration = if (item.conferido) TextDecoration.LineThrough
                                 else TextDecoration.None    // no extra state: driven by DB value
            )
            Text(text = "Qtd: ${item.quantidade}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

`TextDecoration.LineThrough` is derived directly from `item.conferido` — no local boolean needed. Every recomposition triggered by a Checkbox check automatically re-reads the updated value from the Room Flow.

---

### Seed data — 7 deliveries, 28 items

The seed is designed to demonstrate multiple real-world scenarios simultaneously on first launch:

| ID | Client | Category | Items | Status | Seed state |
|---|---|---|---|---|---|
| seed-1 | Ana Paula Ferreira | E-commerce (mixed) | 4 | Pendente | All `conferido=false` |
| seed-2 | Carlos Lima | IT equipment | 5 | Em rota | All `conferido=false` |
| seed-3 | João Silva | Books & stationery | 6 | Pendente | All `conferido=false` |
| seed-4 | Maria Souza | Electronics | 3 | Concluída (`sincronizada=false`) | All `conferido=true` |
| seed-5 | Roberto Alves | Pharmacy | 4 | Pendente | All `conferido=false` |
| seed-6 | Fernanda Costa | Clothing | 3 | Em rota | All `conferido=false` |
| seed-7 | Lucas Mendes | Groceries | 5 | Pendente | All `conferido=false` |

**seed-4 (Maria Souza)** is pre-configured with `conferido = true` on all items and `sincronizada = false` on the delivery — demonstrating the sync badge, the completed delivery layout, and the strikethrough items simultaneously from the very first launch, without the driver needing to interact with the app.

---

## Proto DataStore (typed settings with kotlinx.serialization)

### Why typed DataStore instead of Preferences DataStore

`Preferences DataStore` stores key-value pairs with string keys — a typo in a key name is a silent runtime bug. **Typed DataStore** stores a serialized object; adding a field with the wrong type or name is a compile-time error. It also gives atomic reads and transactional writes without any SQLite overhead.

### Architecture: the `SettingsConfig` journey

```
Switch toggled
      ↓
SettingsViewModel.onDarkThemeChange(true)
      ↓
SettingsRepository.updateDarkTheme(true)   [interface, domain layer]
      ↓
SettingsRepositoryImpl.dataStore.updateData { it.copy(darkTheme = true) }
      ↓
DataStore writes SettingsSerializer.writeTo() → settings.json  (atomic)
      ↓
DataStore.data Flow emits new SettingsConfig
      ↓
SettingsViewModel.init { collect } → _uiState.update { … }
      ↓
MainActivity: val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
      ↓
DeliveryOfflineFirstTheme(darkTheme = settingsState.darkTheme)  ← theme changes immediately
```

### `SettingsConfig` — the serialized model

```kotlin
// SettingsConfig.kt
@Serializable
data class SettingsConfig(
    val darkTheme: Boolean = false,
    val motoristaNome: String = "Motorista"
)
```

Adding a field here with a default value is a backward-compatible change — existing `settings.json` files decode correctly (missing fields use the default).

### `SettingsSerializer` — custom `kotlinx.serialization` Serializer

```kotlin
object SettingsSerializer : Serializer<SettingsConfig> {

    override val defaultValue: SettingsConfig = SettingsConfig()

    override suspend fun readFrom(input: InputStream): SettingsConfig {
        return try {
            Json.decodeFromString(SettingsConfig.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            defaultValue   // corrupted file → return defaults rather than crash
        }
    }

    override suspend fun writeTo(t: SettingsConfig, output: OutputStream) {
        output.write(Json.encodeToString(SettingsConfig.serializer(), t).encodeToByteArray())
    }
}
```

### DataStore delegate — single instance per process

```kotlin
// AppSettingsDataStore.kt
val Context.settingsDataStore: DataStore<SettingsConfig> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer
)
```

The `by dataStore(…)` Kotlin property delegate guarantees that only one `DataStore` instance exists for a given file per process — no race conditions even with concurrent collectors.

### Repository — `catch` for IO resilience

```kotlin
override val settings: Flow<SettingsConfig> = dataStore.data
    .catch { e ->
        if (e is IOException) emit(SettingsConfig())  // graceful degradation
        else throw e                                   // unexpected errors rethrow
    }
```

### Settings screen — Navigation Compose integration

```
EntregasScreen  ─── ⚙ icon ───▶  SettingsScreen
  TopAppBar                         ← back arrow
  shows motoristaNome               dark theme switch
                                    driver name field + Save button
```

Navigation is handled by `AppNavigation.kt` (NavHost with two routes). `SettingsViewModel` is scoped to the `Activity` so the same instance is shared between both screens — the theme changes are reflected immediately across the whole app without a restart.

### Where it shows in the app

1. **Dark Theme toggle** in `SettingsScreen` → persisted to `settings.json` → `DeliveryOfflineFirstTheme(darkTheme = …)` re-applies the color scheme live.
2. **Driver Name** → persisted → appears as subtitle in `EntregasScreen` TopAppBar.
3. The settings icon (⚙) in `EntregasScreen` navigates to `SettingsScreen` via Navigation Compose.

### Compared to the alternatives

| Approach | Type safety | Schema migration | Boilerplate | Chosen |
|---|---|---|---|---|
| Preferences DataStore | ✗ String keys | N/A | Low | |
| Proto DataStore + .proto file | ✓ | Protobuf rules | High | |
| **Typed DataStore + kotlinx.serialization** | **✓** | **Default values** | **Low** | **✓** |

---

## Offline-first, synchronization and retry

### Canonical architecture

```
UI (Compose)
     ↑ observes Flow
┌─────────────────────┐
│  Room = SOURCE OF   │  ← UI ALWAYS reads from local DB, never from network
│       TRUTH         │
└─────────────────────┘
     ↑ writes                   ↑ writes after server ACK
User actions              SyncWorker drains the queue
  (conclude delivery)       when network is available (CONNECTED constraint)
     ↓                             ↑ retry with exponential backoff
┌─────────────────────┐
│  sincronizada=false │  → WorkManager schedules sync on outbox entry
│  (local outbox)     │
└─────────────────────┘
```

### UUID as idempotency key — the pattern that prevents duplicate deliveries

```kotlin
// Entrega.kt — client-generated UUID, domain layer does not care how it's used
data class Entrega(
    val id: String,
    // ...
    val uuid: String = ""  // set by repository at persistence time
)

// EntregaRepositoryImpl.kt — UUID generated here, ViewModel/domain stay clean
private fun Entrega.toEntity() = EntregaEntity(
    // ...
    uuid = uuid.ifBlank { UUID.randomUUID().toString() }
)
```

The repository is the only layer that knows about UUID generation — exactly as the delivery timestamp is generated in the repository, not the ViewModel.

### `SyncWorker` — per-delivery sync with ACK contract

```kotlin
override suspend fun doWork(): Result {
    val pendentes = repository.listarPendentes()  // snapshot: sincronizada=0

    pendentes.forEach { entrega ->
        // Simulates: api.enviar(payload, idempotencyKey = entrega.uuid)
        // Server deduplicates by UUID — if sent twice (crash after POST, before ACK),
        // the second call is a no-op on the server side.
        Log.d(TAG, "POST /entregas idempotency-key=${entrega.uuid}")

        // Mark synced only AFTER the ACK — if the process dies here,
        // WorkManager retries and the entry is retransmitted (with the same UUID).
        repository.marcarSincronizadaPorUuid(entrega.uuid)
    }
    return Result.success()
}
```

**Why this matters:** with the old `marcarTodasSincronizadas()` approach, a crash between "send" and "mark" would mark entries as synced even though the server never received them. The per-UUID approach means the outbox entry survives any partial failure.

### Room Migration 2 → 3

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Empty string default for existing rows — they are seed/mock data,
        // not real outbox events. New deliveries always get a UUID from the repository.
        db.execSQL("ALTER TABLE entrega ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
    }
}
```

This is the second explicit migration in the project. Together with `MIGRATION_1_2`, it demonstrates the evolution of a production schema without ever touching `fallbackToDestructiveMigration()`.

### 6.2 WorkManager — the full picture

| Mechanism | How it's implemented |
|---|---|
| Guaranteed execution | `CoroutineWorker` — survives process death and device reboot |
| Network constraint | `NetworkType.CONNECTED` — no retry waste on airplane mode |
| Exponential backoff | `BackoffPolicy.EXPONENTIAL, 30s` — respects network recovery time |
| No duplicate workers | `enqueueUniqueWork("sync_entregas", KEEP)` |
| Reactive status | `getWorkInfosForUniqueWorkFlow` → `StateFlow<WorkInfo.State?>` in ViewModel |

### Interview script (section 6.3 of study material)

The app demonstrates the exact scenario from the script:

> *"The delivery event was written to Room immediately, with a client-generated UUID, and a pending queue was drained by WorkManager when connectivity returned — with exponential backoff and a network constraint. The UUID guaranteed idempotency: if the same POST was sent twice due to a network drop, the server would deduplicate it."*

**Live demo sequence:** airplane mode → tap "Conclude" on 3 deliveries → badge shows **"3 pending sync"** → disable airplane mode → WorkManager runs → SyncWorker logs `POST /entregas idempotency-key=<uuid>` per delivery → badge zeroes.

---

## AI Assistant — Natural Language Delivery Commands

### Overview

The app exposes a single text field in `EntregasScreen` that accepts both traditional text search (reactive, debounced) and free-text natural language commands processed by **Gemini 2.5 Flash** via **Firebase AI Logic**.

```
User types a command
        ↓
processarComandoNLP(comando)     ← EntregasViewModel
        ↓
NlpRepository.interpretarComando()
        ↓
Gemini 2.5 Flash (Firebase AI Logic, Google AI backend)
  systemInstruction = NlpPrompts.DELIVERY_ASSISTANT_SYSTEM_PROMPT
  responseMimeType  = "application/json"
        ↓
Raw JSON response  →  Json.decodeFromString<NlpCommand>()
        ↓
NlpCommand(action, searchTerm?, targetClient?)
        ↓
when(action) {
  SET_SEARCH_QUERY   → onSearchQueryChange(searchTerm)   — feeds reactive pipeline
  CONCLUDE_DELIVERY  → resolve id from uiState.entregas  → concluirEntrega(id)
  UNKNOWN            → ShowSnackbar("Não entendi o comando ou houve um erro.")
}
```

### Clean Architecture mapping

```
domain/nlp/
  NlpAction.kt          ← enum: SET_SEARCH_QUERY | CONCLUDE_DELIVERY | UNKNOWN
  NlpCommand.kt         ← @Serializable data class (kotlinx.serialization)
  NlpPrompts.kt         ← system instructions constant

domain/repository/
  NlpRepository.kt      ← suspend fun interpretarComando(comando: String): NlpCommand

data/repository/
  NlpRepositoryImpl.kt  ← calls GenerativeModel, parses JSON, always returns safe NlpCommand

di/
  AppModule.kt          ← @Singleton GenerativeModel + NlpRepository providers
```

### System Prompt design

The system prompt (`NlpPrompts.DELIVERY_ASSISTANT_SYSTEM_PROMPT`) is written in English — LLMs follow structured instructions with higher fidelity in English while still processing Portuguese user input normally. Key constraints enforced:

- Output **only** a raw JSON object — no markdown fences, no prose
- `responseMimeType = "application/json"` at the SDK level adds a second enforcement layer
- Three-shot examples are embedded in the prompt to anchor the model to the delivery domain before the first real input

### Supported actions and example commands

#### `SET_SEARCH_QUERY` — filter the delivery list

The extracted `search_term` is injected directly into `_searchQuery`, triggering the `debounce(300) + distinctUntilChanged + flatMapLatest` reactive pipeline. The filter covers **client name, address, and any item name within a delivery** — so a product search surfaces the correct delivery even if the term appears nowhere in the client name or address.

**By client name or address**

| User input | Extracted `search_term` | Effect |
|---|---|---|
| `"pesquisar entregas na Av. Brasil"` | `"Av. Brasil"` | Filters list to Carlos Lima |
| `"vê o que tem pra Ana Paula"` | `"Ana Paula"` | Filters list to Ana Paula Ferreira |
| `"buscar João"` | `"João"` | Filters by name fragment |

**By product / item name**

| User input | Extracted `search_term` | Match in item list | Delivery shown |
|---|---|---|---|
| `"buscar notebook"` | `"notebook"` | "Notebook Dell XPS 15 (i7 / 32GB)" | Carlos Lima |
| `"tem alguma entrega com fone sony?"` | `"fone sony"` | "Fone Sony WH-1000XM5 (preto)" | Maria Souza |
| `"onde está o livro de clean architecture?"` | `"clean architecture"` | "Livro: Clean Architecture (Uncle Bob)" | João Silva |
| `"procurar dipirona"` | `"dipirona"` | "Dipirona Sódica 500mg — 20 comp." | Roberto Alves |
| `"mostrar entrega com vitamina"` | `"vitamina"` | "Vitamina C 1000mg Efervescente (30 un.)" | Roberto Alves |

**How item search works end-to-end**

```
User: "buscar notebook"
        ↓
Gemini (updated prompt) → {"action":"SET_SEARCH_QUERY","search_term":"notebook"}
        ↓
onSearchQueryChange("notebook") → _searchQuery.value = "notebook"
        ↓
debounce(300ms) + distinctUntilChanged + flatMapLatest
        ↓
repository.observarTodas().map { list ->
    list.filter { ec ->
        ec.itens.any { it.nome.contains("notebook", ignoreCase = true) }
        // "Notebook Dell XPS 15" matches → Carlos Lima delivery surfaces
    }
}
        ↓
LazyColumn displays only Carlos Lima's delivery
```

The system prompt was updated to explicitly instruct Gemini that `search_term` can be a **person name, address fragment, or product/item name**, and two item-based few-shot examples were added to anchor the model to this new behaviour.

#### Auto-expand behaviour — card opens when a product is found

Surfacing a delivery is not enough when the user's intent is to see its items. `EntregaCard` uses a `remember` with a composite key to auto-expand whenever the active query matches an item name:

```kotlin
var expandido by remember(searchQuery, entregaComProdutos.itens) {
    val shouldExpand = searchQuery.isNotBlank() &&
        entregaComProdutos.itens.any { it.nome.contains(searchQuery, ignoreCase = true) }
    mutableStateOf(shouldExpand)
}
```

| Scenario | Query | Item match | Card state |
|---|---|---|---|
| Search by client name | `"Ana Paula"` | No item contains "Ana Paula" | Collapsed (default) |
| Search by product | `"notebook"` | "Notebook Dell XPS 15" matches | **Expanded automatically** |
| Query cleared | `""` | `isNotBlank()` → false | Collapsed (reset) |
| User taps collapse | any | — | Collapsed (manual override) |

**Why `remember(searchQuery, entregaComProdutos.itens)` and not plain `remember`**

`remember` without a key caches the initial value for the entire lifetime of the composable. Using a composite key forces the `remember` block to re-execute whenever either input changes:

- `searchQuery` changes → block re-runs → `shouldExpand` is recalculated → card opens or closes accordingly
- `entregaComProdutos.itens` changes (e.g. after a Checkbox check → Room emits a new snapshot) → block re-runs → if the query still matches an item, `shouldExpand` remains `true` and the card stays open
- The user can still collapse the card manually at any time — `expandido` is a `var`, so the `IconButton` click continues to work normally after the initial value is set

#### `CONCLUDE_DELIVERY` — mark a delivery as done

| User input | Extracted `target_client` | Effect |
|---|---|---|
| `"finalizar a entrega da Ana Paula"` | `"Ana Paula"` | Calls `concluirEntrega("1")` |
| `"concluir entrega do Carlos Lima"` | `"Carlos Lima"` | Calls `concluirEntrega("2")` |
| `"marcar entrega da Ana Paula como concluída"` | `"Ana Paula"` | Same as above |
| `"fechar a entrega da Ana Paula"` | `"Ana Paula"` | Same as above |

The model returns the client name; the ViewModel resolves the `id` from `uiState.entregas` using a case-insensitive `firstOrNull` match before calling `concluirEntrega(id)`. The name must be reasonably close to the value in the `cliente` field — full name matches are most reliable.

#### `UNKNOWN` — unrecognised command

Any input that doesn't match a delivery intent (e.g. `"qual o horário de funcionamento?"`) returns `{"action":"UNKNOWN"}` and a Snackbar is shown: *"Não entendi o comando ou houve um erro."*

### UX details

- The **Send button (▷)** and **IME Search key** both trigger `processarComandoNLP()`.
- The button is disabled while `isNlpLoading = true` to prevent double submissions.
- `_searchQuery` is reset to `""` immediately on Send so the reactive filter does not hide the delivery list while the model is processing.
- A `LinearProgressIndicator` replaces the bottom spacer below the search field during loading — no layout shift.

### Error handling

`NlpRepositoryImpl` has a three-level catch strategy and **never throws** to the caller:

| Exception | Cause | Result |
|---|---|---|
| `SerializationException` | Model returned non-JSON text | `NlpCommand(UNKNOWN)` + `Log.w` |
| `Exception` (generic) | Network timeout, App Check failure, API quota | `NlpCommand(UNKNOWN)` + `Log.w` |
| `response.text == null` | Empty model response | `NlpCommand(UNKNOWN)` + `Log.w` |

Filter Logcat by tag `NlpRepositoryImpl` to diagnose failures during development.

### Firebase App Check (debug builds)

Firebase AI Logic enforces App Check. In debug builds, `DebugAppCheckProviderFactory` is installed in `DeliveryApplication.onCreate()`. On first run, it prints a one-time UUID to Logcat:

```
D DebugAppCheckProvider: Enter this debug secret into the Allow list in
  the Firebase Console for your project: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
```

Register that token at **Firebase Console → Build → App Check → your Android app → Manage debug tokens**. Release builds require a [Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider).

> `google-services.json` is excluded from version control (`.gitignore`). Each developer must download their own file from the Firebase Console and place it in `app/` before building.

---

## Firebase Remote Config — feature flags

### What it is and why it's here

Firebase Remote Config stores key-value pairs in the cloud. The app fetches them at runtime and applies them without a Play Store update. This project uses a single boolean flag, `nlp_enabled`, to control whether the Gemini AI feature is active:

| `nlp_enabled` in Firebase Console | Result in EntregasScreen |
|---|---|
| `true` (default) | Placeholder "Buscar ou descreva um comando de IA…", Send button visible, LinearProgressIndicator active |
| `false` | Placeholder "Buscar entrega…", Send button hidden, progress bar hidden — field works as plain search |

### Clean Architecture mapping

```
domain/repository/
  RemoteConfigRepository.kt      ← suspend fun isNlpEnabled(): Boolean

data/repository/
  RemoteConfigRepositoryImpl.kt  ← fetchAndActivate() + getBoolean("nlp_enabled")
                                    catches network failures → falls back to cached/default value

di/
  AppModule.kt                   ← @Singleton FirebaseRemoteConfig with in-app defaults
                                    @Singleton RemoteConfigRepository

presentation/viewmodel/
  EntregasViewModel.kt           ← _nlpEnabled: MutableStateFlow<Boolean> (default true)
                                    reloadRemoteConfig() — public, called by Option C
  SettingsViewModel.kt           ← onReloadRemoteConfig() — triggered by Option B button

presentation/screen/
  EntregasScreen.kt              ← nlpEnabled: Boolean parameter → conditional rendering
  SettingsScreen.kt              ← "Reload Remote Config" button + CircularProgressIndicator
```

### In-app default — fail-safe design

The default value in code is `nlp_enabled = true`. If Firebase is unreachable on first install (no network), the NLP feature remains active. The flag disables only when Firebase explicitly returns `false` and the app successfully receives it.

```kotlin
// AppModule.kt
setDefaultsAsync(mapOf(RemoteConfigRepositoryImpl.KEY_NLP_ENABLED to true))
```

### The 3-layer fetch strategy

Remote Config values are cached by the Firebase SDK. Without any extra logic, changing a flag in the console requires killing the app to see the effect. Three complementary mechanisms solve this at different layers:

#### Option A — Zero cache in debug (`minimumFetchIntervalInSeconds`)

```kotlin
// AppModule.kt
remoteConfigSettings {
    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
}
```

In debug builds, every `fetchAndActivate()` call goes directly to the Firebase server — no local cache blocks it. In release, the interval is 1 hour to respect Firebase's free-tier quota (default is 12 hours). This does not add a new trigger; it makes existing triggers always return fresh values during development.

#### Option B — Manual reload button in SettingsScreen

```kotlin
// SettingsViewModel.kt
fun onReloadRemoteConfig() {
    viewModelScope.launch {
        _uiState.update { it.copy(isReloadingConfig = true) }
        val nlpEnabled = remoteConfigRepository.isNlpEnabled()   // fetchAndActivate()
        _uiState.update { it.copy(isReloadingConfig = false) }
        val status = if (nlpEnabled) "enabled" else "disabled"
        _eventos.emit(SettingsEvent.ShowSnackbar("Remote Config updated — NLP is $status"))
    }
}
```

The "Reload Remote Config" button in `SettingsScreen` shows a `CircularProgressIndicator` while the fetch is in flight and a Snackbar with the result. The fetch activates the new value in the Firebase SDK's in-memory cache — when the user navigates back to `EntregasScreen`, Option C picks it up immediately without a second network call.

#### Option C — Automatic re-fetch on foreground (`repeatOnLifecycle`)

```kotlin
// EntregasScreen.kt
val lifecycleOwner = LocalLifecycleOwner.current
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.reloadRemoteConfig()   // re-reads from cache or server
    }
}
```

`repeatOnLifecycle(RESUMED)` restarts its block every time the screen enters the `RESUMED` state and cancels it on `PAUSED`. This covers two scenarios automatically:

- **App returned from background** — user switched to another app, a console change was made, and the user came back: `reloadRemoteConfig()` runs and `_nlpEnabled` updates reactively.
- **Returned from SettingsScreen** — Option B activated the values; Option C reads the now-warm cache and propagates the result to the UI with no extra network round-trip.

### How all 3 work together — demo sequence

```
1. Open the app                            → init fetchRemoteConfig()  [nlp_enabled=true]
2. Navigate to Settings → tap "Reload"     → Option B: fetchAndActivate() → snackbar "NLP is disabled"
                                             (Firebase SDK cache now has nlp_enabled=false)
3. Navigate back to EntregasScreen         → Option C fires on RESUMED
                                             → reads activated cache (nlp_enabled=false)
                                             → _nlpEnabled.value = false
                                             → Send button disappears, placeholder changes
```

In debug (Option A active), step 2 always hits the server regardless of the 1-hour interval.

---

## Firebase Analytics — NLP Adoption Tracking

### What it measures and why

Adding Firebase Analytics alongside Remote Config closes the feedback loop: instead of only being able to turn the `nlp_enabled` flag on or off, the team can now see **how many users received each value**, **how many actually used the feature**, and **how successful each AI command was** — all broken down by device, country, and app version in the Firebase console.

### Clean Architecture mapping

```
domain/repository/
  AnalyticsRepository.kt       ← interface (no Android/Firebase imports)
                                  logNlpConfigFetched(nlpEnabled, trigger)
                                  logNlpCommandSubmitted()
                                  logNlpCommandResult(action, success)
                                  setNlpFeatureUserProperty(enabled)

data/repository/
  AnalyticsRepositoryImpl.kt   ← wraps FirebaseAnalytics; converts calls to logEvent() + Bundle

di/
  AppModule.kt                 ← @Singleton FirebaseAnalytics.getInstance(context)
                                  @Singleton AnalyticsRepository

presentation/viewmodel/
  EntregasViewModel.kt         ← injects AnalyticsRepository; fires events on config fetch and NLP commands
  SettingsViewModel.kt         ← fires event on manual Remote Config reload

data/worker/
  SyncWorker.kt                ← injects AnalyticsRepository; fires sync_completed after outbox is drained
```

Keeping analytics behind an interface means ViewModels remain unit-testable: tests inject a no-op fake instead of a real `FirebaseAnalytics` instance.

### Events

| Event name | Where it fires | Parameters | What it answers |
|---|---|---|---|
| `nlp_config_fetched` | Every Remote Config fetch | `nlp_enabled` ("true"/"false"), `trigger` | How many users have the flag on vs off? How is it being triggered? |
| `nlp_command_submitted` | User taps Send / presses Search | — | How many users actively try the AI feature? |
| `nlp_command_result` | After Gemini responds | `action` (set_search_query / conclude_delivery / unknown), `success` ("true"/"false") | What commands succeed? What fails? |
| `sync_completed` | `SyncWorker` drains the outbox successfully | `quantity` (Long) | How healthy is the offline-first architecture? How many deliveries accumulate offline? |

#### `trigger` values for `nlp_config_fetched`

| Value | Meaning |
|---|---|
| `init` | First fetch on ViewModel creation (app cold start) |
| `resume` | Re-fetch when `EntregasScreen` re-enters `RESUMED` state (Option C) |
| `manual_reload` | User tapped "Reload Remote Config" in Settings (Option B) |

### User property

```kotlin
analytics.setUserProperty("nlp_feature_enabled", "true") // or "false"
```

`nlp_feature_enabled` is a **persistent user property** — once set, it is attached to every subsequent Analytics event from that device for the entire session, even events that have nothing to do with Remote Config. This allows audience-based filtering in GA4 / BigQuery without post-hoc event joins.

The property is updated every time a Remote Config fetch completes (init, resume, and manual reload), so it always reflects the most recently activated value.

### Code — where each event is dispatched

#### Remote Config fetch (init and resume) — `EntregasViewModel`

```kotlin
private fun fetchRemoteConfig() {
    viewModelScope.launch {
        val enabled = remoteConfigRepository.isNlpEnabled()
        _nlpEnabled.value = enabled
        analyticsRepository.setNlpFeatureUserProperty(enabled)         // persists across the session
        analyticsRepository.logNlpConfigFetched(
            nlpEnabled = enabled,
            trigger = AnalyticsRepositoryImpl.TRIGGER_INIT             // "init"
        )
    }
}

fun reloadRemoteConfig() {
    viewModelScope.launch {
        val enabled = remoteConfigRepository.isNlpEnabled()
        _nlpEnabled.value = enabled
        analyticsRepository.setNlpFeatureUserProperty(enabled)
        analyticsRepository.logNlpConfigFetched(
            nlpEnabled = enabled,
            trigger = AnalyticsRepositoryImpl.TRIGGER_RESUME           // "resume"
        )
    }
}
```

#### Manual reload — `SettingsViewModel`

```kotlin
fun onReloadRemoteConfig() {
    viewModelScope.launch {
        val nlpEnabled = remoteConfigRepository.isNlpEnabled()
        analyticsRepository.setNlpFeatureUserProperty(nlpEnabled)
        analyticsRepository.logNlpConfigFetched(
            nlpEnabled = nlpEnabled,
            trigger = AnalyticsRepositoryImpl.TRIGGER_MANUAL_RELOAD    // "manual_reload"
        )
    }
}
```

#### NLP command lifecycle — `EntregasViewModel`

```kotlin
fun processarComandoNLP(comando: String) {
    viewModelScope.launch {
        analyticsRepository.logNlpCommandSubmitted()   // counts raw intent

        val nlpCommand = nlpRepository.interpretarComando(comando)

        when (nlpCommand.action) {
            NlpAction.SET_SEARCH_QUERY -> {
                analyticsRepository.logNlpCommandResult(
                    action = AnalyticsRepositoryImpl.ACTION_SET_SEARCH_QUERY,
                    success = true
                )
            }
            NlpAction.CONCLUDE_DELIVERY -> {
                val found = resolveDelivery(nlpCommand.targetClient)
                analyticsRepository.logNlpCommandResult(
                    action = AnalyticsRepositoryImpl.ACTION_CONCLUDE_DELIVERY,
                    success = found != null    // false if client name not in list
                )
            }
            NlpAction.UNKNOWN -> {
                analyticsRepository.logNlpCommandResult(
                    action = AnalyticsRepositoryImpl.ACTION_UNKNOWN,
                    success = false
                )
            }
        }
    }
}
```

#### Offline sync — `SyncWorker`

```kotlin
// SyncWorker.kt — fired once the entire outbox batch is confirmed
pendentes.forEach { entrega ->
    repository.marcarSincronizadaPorUuid(entrega.uuid)   // ACK per delivery
}
analyticsRepository.logSyncCompleted(quantity = pendentes.size)
Result.success()
```

The event fires **only on the happy path** — after every delivery in the batch has been individually acknowledged and marked in Room, and before `Result.success()`. Retries and simulated network failures never produce this event, so the data is always clean.

The `quantity` parameter is a **Long** sent via `putLong()`. Firebase Analytics stores it as a numeric value, which means it must be registered in the console as a **Custom Metric** (not a Custom Dimension) so that aggregation functions (sum, average, max) work correctly.

> **Firebase Console → Analytics → Custom Definitions → Custom Metrics → Create**  
> Name: `Entregas Sincronizadas` | Scope: `Event` | Event parameter: `quantity` | Unit: `Standard`

### What `sync_completed` + `quantity` measure in practice

The event connects Firebase Analytics to the health of the offline-first architecture. Each time `SyncWorker` successfully drains the outbox, Analytics receives a data point that answers questions that no other signal in the app can answer:

| Question | How `sync_completed` answers it |
|---|---|
| **How often does the app go offline?** | Event frequency over time — a spike means users were offline more than usual in that period |
| **How many deliveries accumulate before reconnecting?** | Average of `quantity` — a growing average signals users are spending longer offline between syncs |
| **Are there users with extreme offline usage?** | Max of `quantity` — outliers with large batches reveal heavy field scenarios worth stress-testing |
| **Is the WorkManager retry mechanism causing duplicate events?** | Each sync session should produce exactly one event; more than one per session indicates a crash-retry cycle reached success |
| **Does the outbox grow at a sustainable rate?** | `SUM(quantity)` per day vs number of active sessions — the ratio shows how much write pressure the sync pipeline is under |

#### Event dispatch flow inside `SyncWorker`

```
doWork()
  ├── pendentes.isEmpty()
  │     └── Result.success()            ← no event: nothing was in the outbox
  │
  ├── Random.nextFloat() < 0.3f
  │     └── Result.retry()              ← no event: sync did not complete
  │
  └── happy path
        ├── forEach { marcarSincronizadaPorUuid(uuid) }
        ├── logSyncCompleted(quantity = pendentes.size)   ← event fires here
        └── Result.success()
```

This placement guarantees that `quantity` always equals the exact number of deliveries that made it to the server — partial batches (due to per-entry exceptions) would not reach this line.

#### BigQuery — querying offline-first health

```sql
-- Average and max batch size per day (measures offline accumulation)
SELECT
  DATE(event_timestamp / 1000000, "America/Sao_Paulo") AS day,
  COUNT(*)                                              AS sync_sessions,
  ROUND(AVG(
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'quantity')
  ), 1)                                                 AS avg_deliveries_per_sync,
  MAX(
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'quantity')
  )                                                     AS max_deliveries_per_sync
FROM
  `your_project.analytics_XXXXXXXX.events_*`
WHERE
  event_name = 'sync_completed'
GROUP BY day
ORDER BY day DESC;
```

---

### Audience: NLP Adopters (`nlp_feature_enabled = "true"`)

An **audience** in Firebase Analytics is a saved segment of users who share one or more conditions. Once created, it appears as a dimension in all Analytics reports and can be used to target Remote Config, A/B tests, and push notifications.

#### How to create the "NLP Adopters" audience in the Firebase Console

1. Open **Firebase Console → Analytics → Audiences → New audience**
2. Set the audience name to `NLP Adopters`
3. Add condition: **User property** → `nlp_feature_enabled` → **exactly matches (=)** → `true`
4. Click **Save**

Firebase starts computing membership immediately. Users who matched the condition in the last 30 days (the default lookback window) are included automatically.

```
Firebase Console
└── Analytics
    └── Audiences
        └── NLP Adopters
              Condition: user_property[nlp_feature_enabled] = "true"
              Lookback window: 30 days (default)
```

#### What you can do with the "NLP Adopters" audience

| Use case | How |
|---|---|
| View adoption over time | Analytics → Events → `nlp_config_fetched` → filter by `nlp_enabled = true` |
| Compare behavior | All reports → Audience comparison: NLP Adopters vs (not NLP Adopters) |
| A/B test the feature | Remote Config → A/B test → target audience: NLP Adopters |
| Push notifications to adopters | Firebase Messaging → target audience: NLP Adopters |
| Measure usage rate within adopters | Funnel: `nlp_config_fetched (true)` → `nlp_command_submitted` |

#### Adoption funnel — reading the data

```
nlp_config_fetched  (nlp_enabled = "true")     ← total users with the flag ON
         │
         ▼
nlp_command_submitted                           ← users who tried the AI feature at least once
         │
         ▼
nlp_command_result  (success = "true")          ← users who got a successful AI response
```

Create this funnel at **Analytics → Funnels → New funnel**:

| Step | Event | Filter |
|---|---|---|
| 1 | `nlp_config_fetched` | `nlp_enabled` = `true` |
| 2 | `nlp_command_submitted` | — |
| 3 | `nlp_command_result` | `success` = `true` |

The funnel shows conversion rates between steps. A large drop between steps 1 and 2 means users have the feature but are not discovering it — a UX problem. A large drop between steps 2 and 3 means the AI commands are failing often — a quality problem.

#### BigQuery export — querying adoption directly

If BigQuery export is enabled in the Firebase project, the same data is available as SQL:

```sql
-- Daily count of users who received nlp_enabled = true
SELECT
  DATE(event_timestamp / 1000000, "America/Sao_Paulo") AS day,
  COUNT(DISTINCT user_pseudo_id)                        AS adopters
FROM
  `your_project.analytics_XXXXXXXX.events_*`
WHERE
  event_name = 'nlp_config_fetched'
  AND (
    SELECT value.string_value
    FROM UNNEST(event_params)
    WHERE key = 'nlp_enabled'
  ) = 'true'
GROUP BY day
ORDER BY day DESC;
```

```sql
-- NLP command success rate per action type (last 7 days)
SELECT
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'action')  AS action,
  COUNTIF(
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'success') = 'true'
  )                                                                             AS successes,
  COUNT(*)                                                                      AS total,
  ROUND(
    100 * COUNTIF(
      (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'success') = 'true'
    ) / COUNT(*),
    1
  )                                                                             AS success_pct
FROM
  `your_project.analytics_XXXXXXXX.events_*`
WHERE
  event_name = 'nlp_command_result'
  AND _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
GROUP BY action
ORDER BY total DESC;
```

---

## Testing

### Strategy — unit tests vs instrumented tests

The test suite splits responsibilities across two layers:

| Layer | Runner | What it covers | Why |
|---|---|---|---|
| Unit tests (`src/test`) | JVM (no emulator) | Filter predicate logic | Instant feedback, no Android dependencies |
| Instrumented tests (`src/androidTest`) | Emulator / device | UI + ViewModel + Room reactive chain, feature flag rendering | Proves the full stack works end-to-end in a real Android process |

---

### Unit tests — `FiltroBuscaTest`

The `entregasFiltradas` reactive pipeline inside `EntregasViewModel` applies a predicate to every Room emission:

```kotlin
list.filter { ec ->
    query.isBlank() ||
    ec.entrega.cliente.contains(query, ignoreCase = true) ||
    ec.entrega.endereco.contains(query, ignoreCase = true) ||
    ec.itens.any { it.nome.contains(query, ignoreCase = true) }
}
```

This predicate is pure logic over domain objects (`EntregaComProdutos`, `ItemPedido`) — no Android context, no coroutines, no Room. `FiltroBuscaTest` isolates and tests it directly:

```kotlin
class FiltroBuscaTest {

    private fun filtrar(query: String): List<EntregaComProdutos> =
        entregasFixture.filter { ec ->
            query.isBlank() ||
            ec.entrega.cliente.contains(query, ignoreCase = true) ||
            ec.entrega.endereco.contains(query, ignoreCase = true) ||
            ec.itens.any { it.nome.contains(query, ignoreCase = true) }
        }

    @Test fun `busca vazia retorna todas as entregas`() { }
    @Test fun `busca por nome de cliente retorna apenas a entrega correspondente`() { }
    @Test fun `busca por logradouro retorna apenas entrega com esse endereco`() { }
    @Test fun `busca por nome de item retorna a entrega que contem o item`() { }
    @Test fun `busca sem match em nenhum campo retorna lista vazia`() { }
    // + 8 more cases covering case-insensitive, partial match, multi-result queries
}
```

**13 tests, sub-millisecond execution.** No emulator spin-up. A change to the filter predicate breaks the relevant test immediately, before any instrumented test runs.

---

### Test doubles (fakes) — eliminating Firebase from instrumented tests

The production `AppModule` wires three Firebase services: Remote Config, Firebase AI (Gemini), and Firebase Analytics. All three are inappropriate for automated tests:

- Firebase services require a live network and valid API keys
- Responses from Gemini are non-deterministic
- Events sent to Firebase Analytics cannot be asserted in tests

Three fakes in `androidTest/fake/` replace each service:

#### `FakeNlpRepository` — mocks the Gemini AI engine

In production the full call chain is:

```
NlpRepositoryImpl.interpretarComando(comando)
    → GenerativeModel (Gemini 2.5 Flash via Firebase AI)
        → real network call
            → raw JSON response
                → Json.decodeFromString<NlpCommand>()
```

`FakeNlpRepository` implements the same `NlpRepository` interface and returns a pre-configured `NlpCommand` immediately — no network call, no Gemini instance, no API key:

```kotlin
class FakeNlpRepository(
    var response: NlpCommand = NlpCommand(action = NlpAction.UNKNOWN)
) : NlpRepository {
    override suspend fun interpretarComando(comando: String): NlpCommand = response
}
```

Setting `response` before a test lets you simulate any AI outcome: a successful `SET_SEARCH_QUERY`, a `CONCLUDE_DELIVERY`, or an `UNKNOWN` error — all without touching the network.

#### `FakeRemoteConfigRepository` — mocks the `nlp_enabled` feature flag

In production:

```
RemoteConfigRepositoryImpl.isNlpEnabled()
    → FirebaseRemoteConfig.fetchAndActivate()   ← real network call
        → getBoolean("nlp_enabled")
```

`FakeRemoteConfigRepository` returns a hardcoded boolean synchronously:

```kotlin
class FakeRemoteConfigRepository(private val nlpEnabled: Boolean) : RemoteConfigRepository {
    override suspend fun isNlpEnabled(): Boolean = nlpEnabled
}
```

#### `FakeAnalyticsRepository` — no-op for Firebase Analytics

All analytics methods are no-ops so `viewModelScope.launch { analyticsRepository.log…() }` calls succeed silently without sending events to Firebase:

```kotlin
class FakeAnalyticsRepository : AnalyticsRepository {
    override fun logNlpConfigFetched(nlpEnabled: Boolean, trigger: String) = Unit
    override fun logNlpCommandSubmitted() = Unit
    override fun logNlpCommandResult(action: String, success: Boolean) = Unit
    override fun setNlpFeatureUserProperty(enabled: Boolean) = Unit
    override fun logSyncCompleted(quantity: Int) = Unit
}
```

#### How the two Firebase AI fakes work together

The two fakes serve different roles in the `nlp_enabled` flow:

| Fake | Firebase service replaced | Controls |
|---|---|---|
| `FakeRemoteConfigRepository` | Firebase Remote Config | Whether the NLP UI (Send button, AI placeholder) is rendered at all |
| `FakeNlpRepository` | Firebase AI / Gemini 2.5 Flash | What the UI does after the user submits a command |

`FakeRemoteConfigRepository(nlpEnabled = false)` drives `_nlpEnabled = false` in the ViewModel → `EntregasScreen` renders the plain search UI. `FakeRemoteConfigRepository(nlpEnabled = true)` + `FakeNlpRepository(response = NlpCommand(SET_SEARCH_QUERY, "notebook"))` lets a test exercise the full NLP path without a real model.

---

### Instrumented tests — `@UninstallModules` + in-memory Room

Both instrumented test files use `@UninstallModules(AppModule::class)` with an inner `@Module @InstallIn(SingletonComponent::class)` that replaces every production binding:

```kotlin
@HiltAndroidTest
@UninstallModules(AppModule::class)
class BuscaTextualTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {

        // In-memory Room: fresh schema on every process start, no migration needed,
        // no leftover data from previous test runs
        @Provides @Singleton
        fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()

        @Provides @Singleton
        fun provideNlpRepository(): NlpRepository = FakeNlpRepository()

        @Provides @Singleton
        fun provideRemoteConfigRepository(): RemoteConfigRepository =
            FakeRemoteConfigRepository(nlpEnabled = true)

        @Provides @Singleton
        fun provideAnalyticsRepository(): AnalyticsRepository = FakeAnalyticsRepository()

        // ... EntregaDao, EntregaRepository, SettingsDataStore, SettingsRepository
    }
}
```

#### `BuscaTextualTest` — search filter end-to-end (5 tests)

Proves the reactive chain works in a real Android process:

```
OutlinedTextField (testTag: "campo_busca")
  → onValueChange → EntregasViewModel.onSearchQueryChange()
    → _searchQuery StateFlow
      → debounce(300ms) → flatMapLatest
        → Room (in-memory) emits filtered list
          → entregasFiltradas StateFlow
            → collectAsStateWithLifecycle → LazyColumn recomposes
```

| Test | Query typed | Expected result |
|---|---|---|
| `busca_vazia_exibe_todas_as_entregas` | cleared after "Carlos" | All 7 seed deliveries visible again |
| `busca_por_nome_de_cliente_filtra_a_lista` | `"Carlos"` | Only Carlos Lima shown |
| `busca_por_endereco_filtra_a_lista` | `"Alameda Santos"` | Only Roberto Alves shown |
| `busca_por_nome_de_item_filtra_a_lista` | `"Notebook Dell"` | Only Carlos Lima shown (item match, not name/address) |
| `busca_por_nome_de_item_expande_card_automaticamente` | `"Notebook"` | Carlos Lima card auto-expanded, "Notebook Dell XPS 15 (i7 / 32GB)" visible |

`waitUntil` instead of `Thread.sleep` is used throughout — the test waits for the debounce window and coroutine dispatch without adding arbitrary delays.

#### `NlpFlagUiTest` — feature flag UI (4 tests, 2 classes)

Two separate test classes set `nlpEnabled` to opposite values and assert the resulting UI:

```kotlin
// NlpHabilitadoTest — FakeRemoteConfigRepository(nlpEnabled = true)
@Test fun nlp_habilitado_exibe_botao_enviar()
@Test fun nlp_habilitado_exibe_placeholder_com_descricao_de_ia()

// NlpDesabilitadoTest — FakeRemoteConfigRepository(nlpEnabled = false)
@Test fun nlp_desabilitado_oculta_botao_enviar()
@Test fun nlp_desabilitado_exibe_placeholder_simples()
```

The `nlp_desabilitado_oculta_botao_enviar` test uses `waitUntil` to wait past the ViewModel's optimistic default (`_nlpEnabled = MutableStateFlow(true)`) before asserting:

```kotlin
// Wait for the coroutine to overwrite the optimistic default with false
composeTestRule.waitUntil(timeoutMillis = 3_000) {
    composeTestRule
        .onAllNodesWithContentDescription("Enviar comando para IA")
        .fetchSemanticsNodes()
        .isEmpty()
}
```

This makes the test robust against coroutine scheduling — it never asserts on the initial value, only on the settled state after `fetchRemoteConfig()` completes.

---

### Test infrastructure (pre-existing)

| File | Purpose |
|---|---|
| `HiltTestRunner` | Replaces the default test runner with `HiltTestApp_Application` |
| `HiltTestApp` | `@CustomTestApplication(WorkerTestApplication::class)` — triggers Hilt codegen |
| `WorkerTestApplication` | Provides a no-op `WorkerFactory` so `@HiltWorker` classes don't crash in tests that don't exercise WorkManager |

---

## Key dependency versions

| Library | Version |
|---|---|
| Kotlin | 2.0.21 |
| AGP | 9.0.1 |
| Compose BOM | 2024.09.00 |
| Firebase BOM | 34.15.0 |
| Hilt | 2.59.2 |
| Room | 2.7.1 |
| WorkManager | 2.10.1 |
| DataStore | 1.1.4 |
| Navigation Compose | 2.8.9 |
| kotlinx.serialization | 1.7.3 |
| Lifecycle | 2.10.0 |
| KSP | 2.3.9 |
