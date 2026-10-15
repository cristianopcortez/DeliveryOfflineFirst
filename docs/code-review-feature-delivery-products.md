# Pull Request Review: `feature/delivery-products-management`

| Campo | Valor |
|---|---|
| Data | 2026-10-15 |
| Escopo | commits `25f7619` e `556e420` (`8b32938..556e420`), 26 arquivos, +2188/−148 |
| Áreas revisadas | `data/local`, `data/repository`, `domain`, `presentation`, schema `4.json` |
| Regras aplicadas | `.cursor/rules/android-code-review.mdc` |
| Veredito | **MUDANÇAS SOLICITADAS** |

> **Nota sobre o diff:** `feature/delivery-products-management` já foi mergeada na `master`, então `git diff master...HEAD` é vazio. A branch base do repositório é `master` (não existe `main`). Os números de linha abaixo referem-se ao estado em `556e420`.

## Resumo

A feature acrescenta `ItemPedido` com relação 1:N, migration 3→4, checklist de itens, busca por item e card expansível. A modelagem Room está bem feita: `@Index` na FK, `CASCADE`, `exportSchema` e nenhum `fallbackToDestructiveMigration`. Faltam um teste de migration e a sincronização das mudanças de item, e há um risco de seed parcial.

## 🚫 Bloqueadores

### B1. Falta teste de migration 3→4
- **Onde:** `AppDatabase.kt` (`MIGRATION_3_4`). Não há nenhum uso de `MigrationTestHelper` no repositório.
- **Regra violada:** Room, item (d): toda mudança de entidade exige teste de migration.
- **Detalhe:** o `build.gradle.kts` não tinha `androidx.room:room-testing` nem `sourceSets.androidTest.assets.srcDir("$projectDir/schemas")`. A migration atual corresponde ao `4.json`, mas nada garante isso em CI.
- **Correção:** adicionar `room-testing` e o `srcDir` dos schemas, e criar `MigrationTest` que cria o DB na v3, insere uma `entrega`, migra para a v4 com `runMigrationsAndValidate(..., true, MIGRATION_3_4)` e confirma que a linha sobreviveu.
- **Status:** corrigido (ver "Correções aplicadas").

### B2. Marcar item como conferido não entra no outbox
- **Onde:** `EntregasViewModel.kt:173-177`, `EntregaRepositoryImpl.kt:47-48` e `EntregaDao.kt:44-45`.
- **Regra violada:** Offline-first, "toda escrita do usuário persiste com `sincronizada = false`".
- **Problema:** `atualizarConferido` só altera `item_pedido.conferido`. A `entrega` pai continua `sincronizada = 1`, então o `SyncWorker` nunca vê a mudança.
- **Correção:** fazer o update do item e `UPDATE entrega SET sincronizada = 0` na entrega pai, na mesma `@Transaction`.
- **Pendência:** `listarPendentes` devolvia só `EntregaEntity`, sem os itens (`EntregaDao.kt:34`). O payload de sync precisava passar a levar os itens.
- **Status:** ✅ resolvido, incluindo o payload de sync (ver "Correções aplicadas").

## ⚠️ Riscos

### R1. ✅ RESOLVIDO: Seed não atômico pode perder itens de forma permanente
- **Onde:** `EntregasViewModel.kt:265-273`.
- **Problema:** `inserirTodas` e depois `inserirItens` em chamadas separadas. Se o processo morrer entre as duas, as entregas existem sem itens. O guard `contarEntregas() > 0` (linha 267) impede um novo seed, então os itens nunca aparecem.
- **Correção:** mover o seed para a camada `data` com `database.withTransaction { ... }`, ou usar um `RoomDatabase.Callback.onCreate`. Isso também tira lógica de dados do ViewModel e os dados fictícios do código de produção.

### R2. ✅ RESOLVIDO: O comando de IA `CONCLUDE_DELIVERY` ignora a regra de itens conferidos
- **Onde:** `EntregasViewModel.kt:203-208`.
- **Problema:** o botão da UI só habilita com `todosItensConferidos` (`EntregasScreen.kt:458-460, 526`), mas o caminho do LLM chama `concluirEntrega(id)` direto. Uma entrega com itens pendentes pode ser concluída por comando de voz ou texto.
- **Correção:** a invariante deve ficar na camada de dados ou de domínio. O repositório deve recusar a conclusão com itens não conferidos, ou o ViewModel deve validar antes.

### R3. Casamento de cliente por nome (`equals ignoreCase`)
- **Onde:** `EntregasViewModel.kt:204-206`.
- **Problema:** com clientes homônimos, `firstOrNull` escolhe um deles em silêncio. Como a saída do LLM é entrada não confiável, uma conclusão errada gera dado errado. Sugestão: confirmar quando houver mais de um resultado.

### R4. Violações de camada no ViewModel (pré-existentes, não introduzidas por este diff)
- **Onde:** `EntregasViewModel.kt:13, 18, 52`.
- **Problema:** o ViewModel importa `data.worker.SyncWorker` e `data.repository.AnalyticsRepositoryImpl`, e recebe `@ApplicationContext Context`.
- **Regra violada:** Clean Architecture, "presentation só depende de domain" e "ViewModels não recebem Context".
- **Correção:** criar `SyncScheduler` (interface em `domain`, implementação em `data`) e expor as constantes de analytics por `domain`. Seria um bloqueador se o PR as tivesse introduzido, então é dívida a registrar.

### R5. Listas recompostas por referência no `EntregaCard`
- **Onde:** `EntregasScreen.kt:449` e `458`.
- **Problema:** as chaves `remember(..., entregaComProdutos.itens)` mudam a cada emissão do Room, porque `List<ItemPedido>` é recriada. Cada clique no checkbox reseta `expandido` (linha 449) e o card pode fechar sozinho durante a conferência.
- **Correção:** deixar `expandido` fora da chave de itens, por exemplo `rememberSaveable(entrega.id)`, e reaplicar a expansão automática só quando `searchQuery` mudar.
- **Complemento:** `UiState` e `EntregaComProdutos` usam `List` do Kotlin, que o compilador trata como instável. Marcar o domínio com `@Immutable` implicaria dependência de Compose no `domain`, o que é proibido. Uma alternativa é um `stability_config.conf` do compilador Compose.

### R6. Dois pipelines paralelos para a mesma lista
- **Onde:** `EntregasViewModel.kt:73-90` (`entregasFiltradas`) e `143-154` (`observarEntregas`).
- **Problema:** há duas coletas de `repository.observarTodas()`, uma por `flatMapLatest`/`stateIn` e outra por `_uiState`. A tela usa `state.entregas` só para o dropdown e para o contador de pendentes. Isso gera duas queries por mudança.
- **Correção:** unificar em um único `StateFlow<UiState>` com `combine`. `flatMapLatest` reabre a query Room a cada digitação; `observarTodas().combine(searchQuery)` evita isso.

### R7. Logs, relógio e formatação diretos no código de produção
- **Onde:** `EntregasViewModel.kt:110-139` (`Log.d` em produção) e `EntregaRepositoryImpl.kt:35` (`System.currentTimeMillis()` direto).
- **Problema:** o repositório fica pouco testável por causa do `System.currentTimeMillis()`. Considere injetar um `Clock`.
- **Detalhe:** `SimpleDateFormat` é criado a cada recomposição do card (`EntregasScreen.kt:498`). Envolva em `remember(horario)`.

## 💡 Sugestões

- **`EntregasScreen.kt:179`:** `state.entregas.map { ... }` roda em toda recomposição. Use `remember(state.entregas)` ou calcule a lista de clientes no ViewModel. O mesmo vale para `listOf("Todos") + clientes.distinct()` na linha 391.
- **`EntregasScreen.kt:513`:** `itens.forEach` dentro do card sem `key`. Para até uns 10 itens é aceitável, mas use `key(item.id)` para estabilizar o estado do checkbox.
- **`EntregasScreen.kt:130`:** `LaunchedEffect(Unit)` coletando `eventos` é o padrão correto. Como o `MutableSharedFlow` não tem `replay`, um evento emitido sem coletor ativo é perdido. Troque por `Channel(BUFFERED)` + `receiveAsFlow()`.
- **Strings:** textos como `"Concluded at"`, `"Open settings"` e `"Recolher itens"` estão hard-coded e misturam inglês e português. Mova para `strings.xml`.
- **`EntregasScreen.kt:290-293`:** a `key = it.entrega.id` está correta. Acrescente `contentType` se vierem outros tipos de item.
- **DAO:** `marcarTodasSincronizadas` (`EntregaDao.kt:41-42`) pode marcar como sincronizadas entregas que entraram na fila durante o sync. Prefira marcar por uuid.
- **README (+669 linhas):** separar a documentação em outro PR facilitaria o review.

## ✅ Pontos positivos

- `fallbackToDestructiveMigration` ausente, com comentário explicando. As migrations 1→2, 2→3 e 3→4 estão registradas e `exportSchema = true`.
- `item_pedido.entregaId` tem `@Index`, e a migration cria o índice com o mesmo nome (`index_item_pedido_entregaId`).
- `@Transaction` + `@Relation` em `observarTodas`, e `@Insert(IGNORE)` com a explicação do risco do `CASCADE`.
- `uuid` do cliente como idempotency key e `concluirEntrega` marcando `sincronizada = 0` em uma única query.
- `domain/` sem imports de `android.*`, `androidx.*`, `dagger` ou `javax`. Os mappers Entity para model ficam em `data`.
- `key` estável na `LazyColumn` (`EntregasScreen.kt:293`), `derivedStateOf` para o FAB e o contador, `collectAsStateWithLifecycle`, e `UiState` imutável com `asStateFlow()`.
- `agendarSync` usa `NetworkType.CONNECTED`, backoff `EXPONENTIAL` de 30 s e `enqueueUniqueWork` com `KEEP` (linhas 241-254). O `SyncWorker` não faz parte deste diff.

## Veredito: **MUDANÇAS SOLICITADAS**

Havia 2 bloqueadores. B1 impedia validar a migration em CI. B2 deixava mudanças de item fora da fila de sync. R1 e R2 envolvem integridade de dados. **Situação atual:** B1, B2, R1 e R2 estão resolvidos. Restam R3 a R7 e as sugestões, que não bloqueiam o merge.

## Correções aplicadas (B1, B2, R1 e R2)

| Item | Mudança |
|---|---|
| B1 | `libs.versions.toml` e `app/build.gradle.kts`: dependência `androidx.room:room-testing` e `sourceSets.androidTest.assets` apontando para `app/schemas`. Novo `MigrationTest` em `app/src/androidTest`. |
| B2 | `EntregaDao`: `marcarEntregaPaiComoPendente` e `atualizarConferidoMarcandoPendente` (`@Transaction`). `EntregaRepositoryImpl.atualizarConferido` passa a usar o novo método. |

| B2 (payload) | `EntregaDao.listarPendentes()` agora retorna `List<EntregaComProdutosEntity>` (`@Transaction` + `@Relation`). `EntregaRepository.listarPendentes()` retorna `List<EntregaComProdutos>`. `SyncWorker` envia entrega + itens (idempotency key = `uuid`). |
| R2 | Invariante na camada de dados: `EntregaDao.concluirEntregaSeConferida` é um único `UPDATE ... WHERE NOT EXISTS (item com conferido = 0)`. `EntregaRepository.concluirEntrega` retorna `ResultadoConclusao` (`CONCLUIDA`, `ITENS_PENDENTES`, `NAO_ENCONTRADA`). UI e NLP passam pelo mesmo `tentarConcluirEntrega` do ViewModel, que mostra snackbar e não agenda sync quando recusado. O analytics do NLP agora registra `success` real. |
| R1 | `EntregaDao.semearSeVazio` (`@Transaction`) faz o guard `contarEntregas() > 0` e os dois inserts atomicamente. O ViewModel chama `repository.semearSeVazio`. |

**Nota sobre R1:** foi usado `@Transaction` no DAO em vez de `database.withTransaction { }`. Os dois usam o mesmo executor de transações do Room, e a escolha evitou mudar o construtor de `EntregaRepositoryImpl` em 4 módulos Hilt de teste.

**Testes novos** (`ConferirItemOutboxTest`, instrumentado): conclusão recusada com item pendente, conclusão aceita com todos conferidos (e entrando no outbox), `listarPendentes` com itens, seed uma única vez e rollback do seed quando um insert falha.

**Pendências abertas:** R3 a R7 e as sugestões. R4 (violações de camada pré-existentes) e R6 (dois pipelines de lista) são as de maior impacto arquitetural.
