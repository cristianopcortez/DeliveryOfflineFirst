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
│   │   └── (room)      # EntregaEntity, EntregaDao, AppDatabase
│   ├── repository/     # EntregaRepositoryImpl, NlpRepositoryImpl, SettingsRepositoryImpl + mappers
│   └── worker/         # SyncWorker (CoroutineWorker via Hilt)
├── di/                 # AppModule — Hilt SingletonComponent
├── domain/
│   ├── model/          # Entrega (pure Kotlin, no Android deps)
│   ├── nlp/            # NlpAction, NlpCommand, NlpPrompts (Gemini system instructions)
│   └── repository/     # EntregaRepository, NlpRepository, SettingsRepository interfaces
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
    derivedStateOf { state.entregas.count { !it.sincronizada } }
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

// EntregaCard: does not know what "conclude" means
@Composable
private fun EntregaCard(entrega: Entrega, onConcluir: () -> Unit)

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
items(items = entregasFiltradas, key = { it.id }) { entrega ->
    EntregaCard(entrega = entrega, onConcluir = { viewModel.concluirEntrega(entrega.id) })
}
```

Without `key`, inserting one item at the top would recompose the entire list. With `key = { it.id }`, Compose only recomposes the affected item.

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
   AppDatabase.kt               ← version = 3 + MIGRATION_1_2 + MIGRATION_2_3
   EntregaDao.kt                ← @Query UPDATE + observarTodas(): Flow<List<>>
   EntregasScreen.kt            ← card displays "Concluded at HH:mm"
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

| File | DB version | Columns added | Why |
|---|---|---|---|
| `1.json` | 1 | `id`, `cliente`, `endereco`, `status`, `sincronizada` | Initial schema — core delivery fields + offline sync flag |
| `2.json` | 2 | `horarioConclusao INTEGER` (nullable) | Conclusion timestamp — `ALTER TABLE` preserves existing rows; `NULL` for rows created before this migration |
| `3.json` | 3 | `uuid TEXT NOT NULL DEFAULT ''` | Idempotency key for the outbox pattern — empty string default for seed rows; new deliveries always get a `UUID.randomUUID()` from the repository |

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

| User input | Extracted `search_term` | Effect |
|---|---|---|
| `"pesquisar entregas na Av. Brasil"` | `"Av. Brasil"` | Filters list to Carlos Lima |
| `"vê o que tem pra Ana Paula"` | `"Ana Paula"` | Filters list to Ana Paula |
| `"buscar João"` | `"João"` | Filters by name fragment |

The extracted `search_term` is injected directly into `_searchQuery`, triggering the existing `debounce(300) + distinctUntilChanged + flatMapLatest` reactive pipeline.

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
