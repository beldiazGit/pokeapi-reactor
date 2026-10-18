# Diseño — "Baúl de Pokémon y Equipo Activo"

> Desafío Técnico Betwarrior — Java Developer
> Fecha: 2026-10-15
> Proyecto base: `pokeapi-reactor` (librería cliente reactiva de PokéAPI)

---

## 1. Contexto y objetivo

`pokeapi-reactor` **no es una aplicación**: es una *librería cliente* reactiva para PokéAPI
(Spring Boot 2.4.3, Java 11, WebFlux). Hoy no tiene clase `main`
(`spring-boot-maven-plugin` con `skip=true`), ni controllers, ni persistencia, ni
`application.properties`. Su única responsabilidad es
`PokeApiClient.getResource(...)` devolviendo `Mono`/`Flux`.

El desafío pide **hacer crecer un dominio de negocio encima**: gestionar los Pokémon
individuales ("ejemplares") de un entrenador, diferenciando **Equipo Activo** del
**Baúl de Almacenamiento**, con capa de persistencia y endpoints REST.

El enunciado prioriza **claridad arquitectónica, buenas prácticas y trade-offs por
sobre la completitud**. Este documento es, por lo tanto, tan importante como el código:
registra *qué* se decidió y *por qué*, para poder defenderlo en la charla técnica.

---

## 2. Resumen de decisiones

| # | Decisión | Elección | Alternativa descartada |
|---|---|---|---|
| D1 | Estructura del proyecto | Módulo único, por capas (hexagonal) | Multi-módulo Maven |
| D2 | Correr como aplicación | Agregar `@SpringBootApplication`, habilitar plugin, Netty embebido | Seguir como librería |
| D3 | Persistencia | In-memory reactivo detrás de un puerto | H2+R2DBC / Mongo (documentadas como swap) |
| D4 | Raíz del agregado | `Trainer` | `PokemonInstance` como raíz |
| D5 | Límites de almacenamiento | `StorageLimits` configurable (no hardcode) | Constantes en el dominio |
| D6 | Identidad del ejemplar | UUID global desde el día uno | Posición en la lista |
| D7 | Traslado party↔baúl | `PATCH` sobre `location` | Sub-colecciones / acción `/move` |

Cada una se detalla abajo con su trade-off.

---

## 3. Decisiones de arquitectura (con trade-offs)

### D1 — Estructura: módulo único por capas (hexagonal)

El repo hoy es una librería. Para montarle el dominio nuevo evaluamos:

- **Multi-módulo Maven:** dejar `pokeapi-reactor` intacto como módulo-librería y crear
  un módulo app que lo consuma. Máxima separación (fronteras forzadas por el
  compilador), pero más ceremonia de build (parent pom + pom por módulo) y el revisor
  navega dos módulos. Sobre-ingeniería para el alcance.
- **Módulo único por capas (elegido):** convertir el repo en app agregando el nuevo
  bounded context junto al cliente existente. Arranca con un solo comando, el revisor
  lo recorre fácil, y el packaging hexagonal igual da la claridad arquitectónica.

**Trade-off aceptado:** las fronteras entre capas quedan forzadas por convención
(paquetes), no por el build. Se mitiga con un layout claro y el puerto de salida.

```
skaro.pokeapi.*              ← cliente PokéAPI existente (sin tocar)
skaro.pokeapi.trainer/
  ├─ domain/                 ← entidades + value objects + reglas (puro, síncrono)
  ├─ application/            ← casos de uso (orquestan, devuelven Mono/Flux)
  └─ adapter/
       ├─ web/               ← @RestControllers + DTOs + @ControllerAdvice
       ├─ persistence/       ← repositorio in-memory (implementa el puerto)
       └─ pokeapi/           ← PokemonCatalogPort sobre PokeApiClient (puerto salida)
```

**Clave arquitectónica:** el `PokeApiClient` se trata como **puerto de salida**
(`PokemonCatalogPort`), así el dominio no depende de `skaro.pokeapi.*` y se testea sin red.

### D2 — Correr como aplicación

Para exponer y probar las APIs hace falta: (1) clase `main`
`@SpringBootApplication`; (2) habilitar `spring-boot-maven-plugin` (hoy `skip=true`);
(3) WebFlux levanta **Netty embebido en `:8080`**, reactivo de punta a punta;
(4) `application.yml` con `skaro.pokeapi.base-uri` y el `HttpClient`/config del cliente
existente (`@Import(PokeApiReactorCachingConfiguration.class)`); (5) controllers +
persistencia. Resultado: `mvn spring-boot:run` → `curl localhost:8080/...`.

### D3 — Persistencia: in-memory reactivo detrás de un puerto

Stack reactivo ⇒ **JPA/Hibernate bloqueante queda descartado** (bloquearía el event
loop). Opciones reactivas evaluadas:

| | In-memory reactivo | H2 + R2DBC | Mongo reactivo |
|---|---|---|---|
| Infra para correr | Cero | Cero (embebido) | Binario Mongo |
| Persiste al reiniciar | No | Sí | Sí |
| Modela el grafo (IVs, EVs, 4 moves…) | **Directo** | Fricción (aplanar / JSON) | Natural (doc anidado) |
| Boilerplate | Mínimo | Schema + mapeo manual | Medio |

**Elegido: in-memory detrás de `PokemonRepository` (puerto).** Razones: corre al
instante (demo/tests), el `PokemonInstance` es un agregado con value objects anidados
que el `Map` guarda tal cual (R2DBC obligaría a plumbing de mapeo sin valor de diseño),
y **el puerto es la historia de arquitectura**: "la persistencia es un detalle; hoy es
un `Map`, mañana R2DBC/Mongo sin tocar dominio ni casos de uso".

**Escalabilidad (para la defensa):** lo que escala no es el store sino **el puerto**,
que deja la app sin estado (escala horizontal) y el backend intercambiable. Para este
dominio —agregado autocontenido, consultado por id y por `trainerId`— **Mongo** es el
más natural (un agregado = un documento, shard por `trainerId`, sin joins);
**Postgres+R2DBC** es el default conservador si aparecen consultas relacionales o
invariantes entre agregados. No hace falta elegir hoy: el puerto difiere la decisión.

### D4 — Raíz del agregado: `Trainer`

Las reglas que cruzan varios ejemplares (party ≤ N, baúl ≤ M, un ejemplar en
exactamente un lugar, preservar slot al evolucionar) son **invariantes de colección** ⇒
piden una frontera de consistencia. Candidato natural: **`Trainer` como raíz**.

- **`Trainer` raíz (elegido):** invariantes en un solo lugar; un
  entrenador = un documento (calza con Mongo); tamaño acotado (~party 6 + baúl 30). Es
  la respuesta DDD de libro y la más defendible. Contra: el detalle por id entra por el
  trainer (trivial).

  **Concurrencia (optimistic locking).** Que las invariantes vivan en un solo agregado
  no basta por sí solo para evitar carreras cuando el store muta el objeto en su lugar:
  dos capturas concurrentes para el mismo `trainerId` podían intercalar el chequeo de
  tamaño y el `put` y meter 7 en un party de 6. El mecanismo que sostiene la invariante
  bajo concurrencia es **optimistic locking**:
  - **Copy-on-load:** cada lectura (`findByTrainerId` / `loadOrCreate`) devuelve una
    **copia aislada** del agregado, nunca la referencia almacenada; así cada request
    muta su propia copia y jamás corrompe el store.
  - **Versión en el agregado:** `Trainer` lleva un campo `version` (0 para nuevos) que
    el repositorio incrementa en cada save vía `nextVersion()`.
  - **Save con compare-and-swap atómico:** `save` usa `ConcurrentHashMap.compute`, cuya
    función de remapeo corre bajo el lock del bin de esa clave; compara la versión
    almacenada contra la de la copia entrante y, si difieren (otro escritor ganó),
    lanza `OptimisticLockException`; si coinciden, reemplaza por `nextVersion()`. El
    chequeo-y-reemplazo es un único paso atómico por clave.
  - **Reintento en los casos de uso:** `capture`, `move` y `evolve` envuelven el tramo
    LOAD→MUTATE→SAVE en `Mono.defer(...).retryWhen(Retry.max(N).filter(...))`; ante
    `OptimisticLockException` recargan una copia fresca y re-aplican el mismo cambio
    (la captura reusa la MISMA instancia/UUID, construida fuera del bloque reintentado).

  De modo que la invariante del party/baúl se sostiene bajo concurrencia: no hay lost
  updates ni se excede el máximo. Un adapter de BD real (R2DBC/Mongo) usaría exactamente
  el mismo patrón de optimistic locking con una columna/campo `version`.
- **`PokemonInstance` raíz (descartado):** agregados más chicos que escalan por
  separado, pero "party ≤ 6" pasa a cruzar agregados ⇒ necesita transacción u optimistic
  locking para evitar que dos capturas concurrentes metan 7 (invariante entre agregados).

`Trainer` expone el comportamiento: `capture()`, `moveToParty()`, `moveToBox()`,
`evolve()`. Nadie agrega al party salvo pasando por el `Trainer`.

### D5 — Límites configurables (no hardcode)

**El enunciado no da número.** El 6 (party) y 30 (baúl) vienen del juego canónico; son
**política de negocio que elegimos**. Se separa *la regla* de *el valor*:

- La **regla** ("el party tiene un máximo") es invariante del agregado `Trainer` y se
  hace cumplir ahí (`capture()`/`moveToParty()` tiran `PartyFullException`).
- El **valor** es configuración inyectada como VO `StorageLimits(partyMax, boxMax)`:

```yaml
trainer:
  party: { max-size: 6 }
  box:   { max-size: 30 }
```

Ventajas: dominio testeable (inyectar `StorageLimits(2,3)` y probar el borde) y cambiar
el límite sin recompilar.

### D6 — Identidad global del ejemplar (hedge barato)

`PokemonInstance` tiene **UUID global desde el día uno**, no "posición en la lista", y el
repo puede direccionarlo por ese id **aunque físicamente viva dentro del `Trainer`**.
Costo casi nulo, alto valor de opción: si el baúl se vuelve ilimitado a futuro (ver §7),
el split a agregados-por-instancia es *mover datos*, no *rediseñar identidad*.

### D7 — Traslado party↔baúl: `PATCH` sobre `location`

| Opción | Forma | Veredicto |
|---|---|---|
| **A (elegida)** | `PATCH .../pokemon/{id}` `{"location":"PARTY"}` | Un endpoint, ambas direcciones; `location` es un atributo de estado del ejemplar |
| B | `PUT .../party/{id}` / `PUT .../box/{id}` | Resource-oriented pero parte en dos una operación atómica |
| C | `POST .../pokemon/{id}/move` `{target}` | Explícito pero verbo en URL, menos REST |

---

## 4. Modelo de dominio

### Agregado

```
Trainer (aggregate root)          ← dueño de las invariantes de almacenamiento
  ├─ Party   (máx partyMax, slots 0..partyMax-1)
  └─ Box     (máx boxMax)
        └─ PokemonInstance (entity, UUID)   ← el "ejemplar"
```

### `PokemonInstance` y sus Value Objects (auto-validantes en el constructor)

| VO | Regla que encapsula |
|---|---|
| `IndividualValues` (IVs) | 6 stats, cada una **0–31** |
| `EffortValues` (EVs) | 6 stats, cada una **0–252**, **suma ≤ 510** |
| `Gender` | enum `MALE / FEMALE / GENDERLESS` |
| `Nature` | 1 de 25 (nombre/ref) |
| `Moveset` | lista **≤ 4**, sin duplicados |
| `HeldItem` | El objeto que el ejemplar **sostiene/lleva encima** (ej. *Leftovers*, una *Berry*). Estado mutable, **máx 1** → `Optional<HeldItem>`. Guarda solo la referencia al `Item` de PokéAPI (lo estático vive allá). Distinto de la pokéball, que es dato histórico inmutable en `OriginMetadata`. |
| `OriginMetadata` | OT id, pokéball, fecha de captura, nivel inicial, ubicación de origen (inmutable) |
| `SpeciesRef` | referencia a la especie (id/name); los atributos estáticos **no** se copian |

`Stat` como `enum {HP, ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED}`; IVs/EVs
como `Map<Stat,Integer>` → validación en loop, extensible.

### Dónde viven las reglas

- **`domain/`** — entidades + VOs, **puro y síncrono**, sin Spring ni Reactor.
- **`application/`** — casos de uso: `CapturePokemon`, `MovePokemon`, `ListParty`,
  `ListBox`, `GetInstance`, `EvolvePokemon`. Devuelven `Mono`/`Flux`.
- **`adapter/pokeapi/`** — `PokemonCatalogPort` sobre `PokeApiClient`.

**Patrón reactivo clave:** lo asíncrono se queda en los bordes. El caso de uso primero
**trae** de PokéAPI lo que necesita (`Mono`) y *después* invoca la lógica de dominio pura
pasándole esos datos ya resueltos. El dominio nunca toca `Mono`/red ⇒ trivial de testear.

### Validaciones: dominio puro vs PokéAPI

- **Puro (sin red):** rangos de IVs/EVs y totales, moveset ≤4, held item ≤1, límites
  party/baúl, naturaleza ∈ 25.
- **Contra PokéAPI (puerto de salida):** la especie existe; la habilidad pertenece a la
  especie; compatibilidad de género (`gender_rate`). La *learnability* de movimientos es
  costosa (chequear la lista de aprendizaje de cada move) → documentada como extensión
  configurable, no default.

---

## 5. API REST

Trainer en el path (sin auth en el alcance; `trainerId` como identificador). Prefijo
`/api/v1`. El `Trainer` se crea implícito en la primera captura.

| # | Método + ruta | Qué hace | Éxito | Errores |
|---|---|---|---|---|
| 1 | `POST /api/v1/trainers/{trainerId}/pokemon` | Captura/crea; auto-asigna party→baúl | `201` + `Location` | `422` inválido · `409` party+baúl llenos |
| 2 | `GET /api/v1/trainers/{trainerId}/pokemon/{id}` | Detalle (dato guardado) | `200` | `404` |
| 3 | `GET /api/v1/trainers/{trainerId}/party` | Equipo activo — **vista compuesta** (ejemplar + especie PokéAPI), por slot | `200` | `404` trainer |
| 4 | `GET /api/v1/trainers/{trainerId}/box` | Baúl, paginado `?page=&size=` | `200` | `404` trainer |
| 5 | `PATCH /api/v1/trainers/{trainerId}/pokemon/{id}` | Traslado `{"location":"PARTY"\|"BOX"}` | `200` | `404` · `409` destino lleno |
| 6 | `POST /api/v1/trainers/{trainerId}/pokemon/{id}/evolve` *(bonus)* | Evolución, preserva id/slot/genética | `200` | `404` · `422` camino inválido |

### Notas de diseño de la API

- **#1 auto-placement:** el server decide party vs baúl; la respuesta incluye dónde cayó
  (`location` + `slot`). Se descarta dejar que el cliente elija destino para no duplicar
  la lógica de límites.
- **#2 vs #3:** el detalle es dato guardado (barato, sin red); la vista compuesta
  —cruzar con PokéAPI— es *solo* el party (lo pide el PDF), para no pegarle a PokéAPI en
  cada lectura. Enriquecer el detalle se expone como `?expand=species` (extensión).
- **#3 composición reactiva:** `Flux` de instancias → `flatMap` con
  `catalogPort.getPokemon(species)` → DTO; el caching de la lib evita N llamadas a la
  misma especie.
- **#4 baúl paginado** desde el día uno (colección potencialmente grande); dato guardado,
  no compuesto, para mantenerlo liviano.
- **#6 evolución como acción** (sub-recurso verbo): no es CRUD. Preserva identidad (mismo
  `id`), slot y genética; cambia especie + habilidad (a una válida de la nueva especie).
  Valida que la especie destino sea evolución **directa** en la cadena (sin requisitos
  situacionales de nivel/objeto/felicidad).

### Manejo de errores

Excepciones de dominio tipadas (`PartyFullException`, `BoxFullException`,
`InstanceNotFoundException`, `InvalidEffortValuesException`, `InvalidEvolutionException`…)
mapeadas por `@ControllerAdvice` a códigos HTTP, idealmente con
`application/problem+json` (RFC-7807).

---

## 6. Estrategia de testing

- **Dominio:** tests unitarios puros de VOs (bordes de IVs/EVs) y del agregado `Trainer`
  (límites, slot, traslados) — sin Spring, sin red, inyectando `StorageLimits` chicos.
- **Casos de uso:** con un `PokemonCatalogPort` y `PokemonRepository` fake/in-memory.
- **Web:** `WebTestClient` sobre los controllers (códigos HTTP, payloads, errores).
- **Adapter PokéAPI:** se apoya en los tests existentes de la librería (`mockwebserver`).

---

## 7. Evolución futura: ¿y si el baúl se vuelve ilimitado?

Hoy el requisito es **acotado** ⇒ se modela lo más simple y correcto (`Trainer` raíz,
YAGNI). Si a futuro el baúl fuera ilimitado, con `Trainer` raíz aparecen: carga/guardado
del agregado entero, contención de escritura en la raíz, límite de 16MB por documento en
Mongo, y paginación imposible como unidad de carga (anti-patrón *large-cluster
aggregate*).

**El seam de refactor:** "party ≤ 6" es **invariante dura** (necesita frontera
transaccional); "baúl ≤ M" es **política blanda** (si es ilimitado, deja de ser
invariante y no necesita vivir dentro del agregado). Migración natural = **híbrido**:

- El **party** queda como agregado chico y acotado.
- Los ejemplares del **baúl** pasan a ser agregados `PokemonInstance` propios por id.
- Mover party↔baúl pasa a ser un caso de uso que **coordina dos agregados** (optimistic
  locking / compensación).

**Honestidad del trade-off:** el puerto abstrae *el motor de base de datos*, no *la forma
del agregado* → esto es un **refactor acotado, no un cambio de config**. El blast radius
queda contenido por el hexagonal: `PokemonInstance` y los VOs **no se tocan**; la capa web
**no se toca**; cambian la frontera del agregado, los puertos de repo y el caso de uso de
"mover". El **UUID global (D6)** deja esa costura preparada desde hoy.

**Cómo defenderlo:** cuestionar "ilimitado" (¿miles o cientos?) y diseñar a la
cardinalidad real; nombrar el seam (invariante dura vs política blanda); mostrar el camino
híbrido; admitir el costo real.

---

## 8. Alcance explícito

**Incluido:** dominio + VOs, persistencia in-memory tras puerto, endpoints #1–#5,
validaciones de dominio + validación contra PokéAPI (especie/habilidad/género), manejo de
errores, tests, app ejecutable, README actualizado.

**Bonus (si alcanza el tiempo):** endpoint #6 (evolución) con cadenas simples y
ramificadas (Eevee).

**Fuera de alcance (documentado como futuro):** autenticación/registro de entrenadores,
persistencia durable (R2DBC/Mongo), learnability de movimientos contra PokéAPI, trading
entre entrenadores, baúl con múltiples cajas.
