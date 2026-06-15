# Delivery Offline First

Android offline-first delivery app built with Jetpack Compose, MVVM + Clean Architecture, Room, StateFlow and WorkManager.

> Built as a focused portfolio project to demonstrate senior-level Android patterns in a realistic field-delivery context: a driver app that works without network connectivity and synchronizes data when the connection is restored.

---

## Architecture

```
deliveryofflinefirst/
├── data/
│   ├── local/          # Room — EntregaEntity, EntregaDao, AppDatabase
│   ├── repository/     # EntregaRepositoryImpl + entity↔domain mappers
│   └── worker/         # SyncWorker (CoroutineWorker via Hilt)
├── di/                 # AppModule — Hilt SingletonComponent
├── domain/
│   ├── model/          # Entrega (pure Kotlin, no Android deps)
│   └── repository/     # EntregaRepository interface (contract)
└── presentation/
    ├── screen/         # EntregasScreen (stateless composables)
    └── viewmodel/      # EntregasViewModel, EntregasUiState, EntregasEvent
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

## Bloco A — Jetpack Compose & State Management

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

### `remember` vs `rememberSaveable` — choosing the right tool

```kotlin
// Ephemeral UI state: dropdown open/close does not need to survive rotation
var expanded by remember { mutableStateOf(false) }

// Search and filter state live in the ViewModel — survives rotation via ViewModel lifecycle,
// which is even better than rememberSaveable (no Bundle size limit)
val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
```

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

## Bloco B — Coroutines & Flow

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

The reactive search pipeline in `EntregasViewModel` demonstrates five Bloco B operators in sequence:

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

## Bloco C — Room Migration (schema versioning without data loss)

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

---

## Key dependency versions

| Library | Version |
|---|---|
| Kotlin | 2.0.21 |
| AGP | 9.0.1 |
| Compose BOM | 2024.09.00 |
| Hilt | 2.59.2 |
| Room | 2.7.1 |
| WorkManager | 2.10.1 |
| Lifecycle | 2.10.0 |
| KSP | 2.3.9 |
