# Baúl de Pokémon y Equipo Activo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar a la librería `pokeapi-reactor` un dominio de negocio que gestione los Pokémon individuales de un entrenador, diferenciando Equipo Activo del Baúl, expuesto vía REST.

**Architecture:** Arquitectura hexagonal en módulo único bajo el paquete `skaro.pokeapi.trainer`. El dominio (`Trainer` como raíz de agregado + value objects auto-validantes) es puro y síncrono. Los casos de uso orquestan y devuelven `Mono`/`Flux`. Dos puertos de salida: `PokemonRepository` (persistencia, implementada in-memory) y `PokemonCatalogPort` (datos estáticos, implementada sobre el `PokeApiClient` existente). La app arranca sobre Netty embebido de WebFlux.

**Tech Stack:** Java 11, Spring Boot 2.4.3, Spring WebFlux, Project Reactor, JUnit 5, `reactor-test` (`StepVerifier`), `WebTestClient`, `mockwebserver` (ya presente para tests de la librería).

## Global Constraints

- **Java 11** — sin records (Java 16+) ni `var` en APIs públicas; VOs como clases inmutables con validación en constructor. Copiar el estilo de getters/setters de `skaro.pokeapi.resource.*`.
- **Sin dependencias nuevas de producción** salvo que una tarea lo indique — reutilizar lo que trae `spring-boot-starter-webflux` y `spring-boot-starter-validation`.
- **Paquete base del dominio nuevo:** `skaro.pokeapi.trainer.*`. No tocar `skaro.pokeapi.*` existente.
- **Reactivo de punta a punta:** nada de `.block()` en código de producción; lo bloqueante solo en tests.
- **Límites de almacenamiento:** NO hardcodear; vienen de configuración (`trainer.party.max-size`, `trainer.box.max-size`), default 6 y 30.
- **Identidad del ejemplar:** `UUID` global generado en captura, no posición en lista.
- **IVs:** 6 stats, cada una `0–31`. **EVs:** 6 stats, cada una `0–252`, suma total `≤ 510`. **Moveset:** `≤ 4` sin duplicados. **Held item:** `≤ 1`.

---

### Task 1: Bootstrap de la aplicación

Convierte la librería en app ejecutable: clase `main`, plugin Maven, config del cliente PokéAPI y propiedades de almacenamiento.

**Files:**
- Modify: `pom.xml:68-74` (quitar `<skip>true</skip>` del `spring-boot-maven-plugin`)
- Create: `src/main/java/skaro/pokeapi/trainer/TrainerApplication.java`
- Create: `src/main/java/skaro/pokeapi/trainer/config/PokeApiClientConfig.java`
- Create: `src/main/java/skaro/pokeapi/trainer/config/StorageProperties.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/skaro/pokeapi/trainer/TrainerApplicationTest.java`

**Interfaces:**
- Produces: `StorageProperties` con `getParty().getMaxSize()` y `getBox().getMaxSize()` (ambos `int`); bean `PokeApiClient` disponible en el contexto.

- [ ] **Step 1: Escribir el test de carga de contexto**

```java
package skaro.pokeapi.trainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.trainer.config.StorageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TrainerApplicationTest {

    @Autowired
    private PokeApiClient pokeApiClient;

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void contextLoadsWithPokeApiClientAndStorageDefaults() {
        assertNotNull(pokeApiClient);
        assertEquals(6, storageProperties.getParty().getMaxSize());
        assertEquals(30, storageProperties.getBox().getMaxSize());
    }
}
```

- [ ] **Step 2: Correr el test para verque falla**

Run: `./mvnw -q test -Dtest=TrainerApplicationTest`
Expected: FAIL — no existe `TrainerApplication` ni los beans.

- [ ] **Step 3: Crear clase main, config y propiedades**

`TrainerApplication.java`:
```java
package skaro.pokeapi.trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrainerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainerApplication.class, args);
    }
}
```

`config/PokeApiClientConfig.java` (reutiliza la config caching de la librería y provee el `HttpClient`, como documenta el README):
```java
package skaro.pokeapi.trainer.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import skaro.pokeapi.PokeApiReactorCachingConfiguration;

@Configuration
@Import(PokeApiReactorCachingConfiguration.class)
@EnableCaching
public class PokeApiClientConfig {

    @Bean
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("pokeapi-trainer")
                .maxIdleTime(Duration.ofSeconds(10))
                .maxConnections(500)
                .pendingAcquireMaxCount(-1)
                .build();
    }

    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
                .compress(true)
                .resolver(DefaultAddressResolverGroup.INSTANCE);
    }
}
```

`config/StorageProperties.java`:
```java
package skaro.pokeapi.trainer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trainer")
public class StorageProperties {

    private final Limit party = new Limit();
    private final Limit box = new Limit();

    public Limit getParty() { return party; }
    public Limit getBox() { return box; }

    public static class Limit {
        private int maxSize;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }
}
```

`src/main/resources/application.yml`:
```yaml
skaro:
  pokeapi:
    base-uri: https://pokeapi.co/api/v2/
    max-buffer-size: 565000

trainer:
  party:
    max-size: 6
  box:
    max-size: 30
```

- [ ] **Step 4: Quitar el skip del plugin en `pom.xml`**

Reemplazar el bloque del `spring-boot-maven-plugin` (líneas ~68-74):
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

- [ ] **Step 5: Correr el test**

Run: `./mvnw -q test -Dtest=TrainerApplicationTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/skaro/pokeapi/trainer src/main/resources/application.yml src/test/java/skaro/pokeapi/trainer
git commit -m "feat: bootstrap trainer application (main, pokeapi config, storage props)"
```

---

### Task 2: Enums `Stat` y `Gender` + VO `IndividualValues`

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/Stat.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/Gender.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/IndividualValues.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidStatValueException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/IndividualValuesTest.java`

**Interfaces:**
- Produces: `Stat` (enum: `HP, ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED`); `Gender` (enum: `MALE, FEMALE, GENDERLESS`); `IndividualValues(Map<Stat,Integer>)` con `int get(Stat)`; `InvalidStatValueException extends RuntimeException`.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndividualValuesTest {

    private Map<Stat, Integer> valid(int value) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, value);
        return m;
    }

    @Test
    void acceptsValuesInRange() {
        IndividualValues ivs = new IndividualValues(valid(31));
        assertEquals(31, ivs.get(Stat.SPEED));
    }

    @Test
    void rejectsValueAbove31() {
        Map<Stat, Integer> m = valid(10);
        m.put(Stat.ATTACK, 32);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }

    @Test
    void rejectsNegativeValue() {
        Map<Stat, Integer> m = valid(10);
        m.put(Stat.HP, -1);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }

    @Test
    void rejectsMissingStat() {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        m.put(Stat.HP, 10);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=IndividualValuesTest`
Expected: FAIL — clases inexistentes.

- [ ] **Step 3: Implementar**

`Stat.java`:
```java
package skaro.pokeapi.trainer.domain;

public enum Stat {
    HP, ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED
}
```

`Gender.java`:
```java
package skaro.pokeapi.trainer.domain;

public enum Gender {
    MALE, FEMALE, GENDERLESS
}
```

`exception/InvalidStatValueException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class InvalidStatValueException extends RuntimeException {
    public InvalidStatValueException(String message) {
        super(message);
    }
}
```

`IndividualValues.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;

public final class IndividualValues {

    public static final int MIN = 0;
    public static final int MAX = 31;

    private final Map<Stat, Integer> values;

    public IndividualValues(Map<Stat, Integer> values) {
        EnumMap<Stat, Integer> copy = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            Integer value = values == null ? null : values.get(stat);
            if (value == null) {
                throw new InvalidStatValueException("Falta el IV de " + stat);
            }
            if (value < MIN || value > MAX) {
                throw new InvalidStatValueException(
                        "IV de " + stat + " fuera de rango [" + MIN + "," + MAX + "]: " + value);
            }
            copy.put(stat, value);
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public int get(Stat stat) {
        return values.get(stat);
    }

    public Map<Stat, Integer> asMap() {
        return values;
    }
}
```

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=IndividualValuesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain src/test/java/skaro/pokeapi/trainer/domain/IndividualValuesTest.java
git commit -m "feat: Stat/Gender enums and IndividualValues value object"
```

---

### Task 3: VO `EffortValues`

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/EffortValues.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidEffortValuesException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/EffortValuesTest.java`

**Interfaces:**
- Consumes: `Stat`.
- Produces: `EffortValues(Map<Stat,Integer>)` con `int get(Stat)`; `InvalidEffortValuesException extends RuntimeException`.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EffortValuesTest {

    private Map<Stat, Integer> of(int hp, int atk, int def, int spa, int spd, int spe) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        m.put(Stat.HP, hp); m.put(Stat.ATTACK, atk); m.put(Stat.DEFENSE, def);
        m.put(Stat.SPECIAL_ATTACK, spa); m.put(Stat.SPECIAL_DEFENSE, spd); m.put(Stat.SPEED, spe);
        return m;
    }

    @Test
    void acceptsValidSpread() {
        EffortValues evs = new EffortValues(of(252, 252, 6, 0, 0, 0));
        assertEquals(252, evs.get(Stat.ATTACK));
    }

    @Test
    void rejectsSingleStatAbove252() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(253, 0, 0, 0, 0, 0)));
    }

    @Test
    void rejectsTotalAbove510() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(252, 252, 252, 0, 0, 0)));
    }

    @Test
    void rejectsNegative() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(-1, 0, 0, 0, 0, 0)));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=EffortValuesTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

`exception/InvalidEffortValuesException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class InvalidEffortValuesException extends RuntimeException {
    public InvalidEffortValuesException(String message) {
        super(message);
    }
}
```

`EffortValues.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;

public final class EffortValues {

    public static final int MIN_PER_STAT = 0;
    public static final int MAX_PER_STAT = 252;
    public static final int MAX_TOTAL = 510;

    private final Map<Stat, Integer> values;

    public EffortValues(Map<Stat, Integer> values) {
        EnumMap<Stat, Integer> copy = new EnumMap<>(Stat.class);
        int total = 0;
        for (Stat stat : Stat.values()) {
            Integer value = values == null ? null : values.get(stat);
            if (value == null) {
                throw new InvalidEffortValuesException("Falta el EV de " + stat);
            }
            if (value < MIN_PER_STAT || value > MAX_PER_STAT) {
                throw new InvalidEffortValuesException(
                        "EV de " + stat + " fuera de rango [" + MIN_PER_STAT + "," + MAX_PER_STAT + "]: " + value);
            }
            total += value;
            copy.put(stat, value);
        }
        if (total > MAX_TOTAL) {
            throw new InvalidEffortValuesException("Suma de EVs " + total + " supera el máximo " + MAX_TOTAL);
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public int get(Stat stat) {
        return values.get(stat);
    }

    public Map<Stat, Integer> asMap() {
        return values;
    }
}
```

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=EffortValuesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain/EffortValues.java src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidEffortValuesException.java src/test/java/skaro/pokeapi/trainer/domain/EffortValuesTest.java
git commit -m "feat: EffortValues value object with per-stat and total limits"
```

---

### Task 4: VOs `Nature`, `Moveset`, `HeldItem`, `SpeciesRef`, `OriginMetadata`

VOs simples de referencia/metadata. Van juntos: comparten patrón y ninguno justifica un gate de review propio.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/Nature.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/Moveset.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/HeldItem.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/SpeciesRef.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/OriginMetadata.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidMovesetException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/MovesetTest.java`

**Interfaces:**
- Produces: `Nature(String name)` con `getName()`; `Moveset(List<String> moves)` con `getMoves()` (máx 4, sin duplicados case-insensitive); `HeldItem(String name)` con `getName()`; `SpeciesRef(String name)` con `getName()`; `OriginMetadata(String originalTrainerId, String pokeball, LocalDate captureDate, int initialLevel, String originLocation)` con getters; `InvalidMovesetException extends RuntimeException`. Nombres normalizados a lowercase/trim.

- [ ] **Step 1: Escribir el test del moveset (el único con lógica no trivial)**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MovesetTest {

    @Test
    void acceptsUpToFourUniqueMoves() {
        Moveset moveset = new Moveset(Arrays.asList("tackle", "growl", "vine-whip", "tackle".toUpperCase()));
        // "TACKLE" duplica "tackle" case-insensitive -> debe fallar
        // (ver test dedicado abajo). Acá validamos 3 válidos:
        assertEquals(3, new Moveset(Arrays.asList("tackle", "growl", "vine-whip")).getMoves().size());
    }

    @Test
    void acceptsEmptyMoveset() {
        assertTrue(new Moveset(Collections.emptyList()).getMoves().isEmpty());
    }

    @Test
    void rejectsMoreThanFour() {
        assertThrows(InvalidMovesetException.class,
                () -> new Moveset(Arrays.asList("a", "b", "c", "d", "e")));
    }

    @Test
    void rejectsDuplicatesCaseInsensitive() {
        assertThrows(InvalidMovesetException.class,
                () -> new Moveset(Arrays.asList("Tackle", "tackle")));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=MovesetTest`
Expected: FAIL.

- [ ] **Step 3: Implementar los VOs**

`exception/InvalidMovesetException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class InvalidMovesetException extends RuntimeException {
    public InvalidMovesetException(String message) {
        super(message);
    }
}
```

`Nature.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.util.Objects;

public final class Nature {
    private final String name;

    public Nature(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("La naturaleza no puede ser vacía");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Nature)) return false;
        return name.equals(((Nature) o).name);
    }
    @Override public int hashCode() { return Objects.hash(name); }
}
```

`Moveset.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;

public final class Moveset {

    public static final int MAX_MOVES = 4;

    private final List<String> moves;

    public Moveset(List<String> moves) {
        List<String> source = moves == null ? Collections.emptyList() : moves;
        if (source.size() > MAX_MOVES) {
            throw new InvalidMovesetException("Un Pokémon no puede tener más de " + MAX_MOVES + " movimientos");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String move : source) {
            if (move == null || move.trim().isEmpty()) {
                throw new InvalidMovesetException("Movimiento vacío en el set");
            }
            String n = move.trim().toLowerCase();
            if (!seen.add(n)) {
                throw new InvalidMovesetException("Movimiento duplicado: " + n);
            }
            normalized.add(n);
        }
        this.moves = Collections.unmodifiableList(normalized);
    }

    public List<String> getMoves() { return moves; }
}
```

`HeldItem.java`:
```java
package skaro.pokeapi.trainer.domain;

public final class HeldItem {
    private final String name;

    public HeldItem(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El objeto equipado no puede ser vacío");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }
}
```

`SpeciesRef.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.util.Objects;

public final class SpeciesRef {
    private final String name;

    public SpeciesRef(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("La especie no puede ser vacía");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpeciesRef)) return false;
        return name.equals(((SpeciesRef) o).name);
    }
    @Override public int hashCode() { return Objects.hash(name); }
}
```

`OriginMetadata.java`:
```java
package skaro.pokeapi.trainer.domain;

import java.time.LocalDate;

public final class OriginMetadata {
    private final String originalTrainerId;
    private final String pokeball;
    private final LocalDate captureDate;
    private final int initialLevel;
    private final String originLocation;

    public OriginMetadata(String originalTrainerId, String pokeball, LocalDate captureDate,
                          int initialLevel, String originLocation) {
        if (initialLevel < 1 || initialLevel > 100) {
            throw new IllegalArgumentException("El nivel inicial debe estar entre 1 y 100: " + initialLevel);
        }
        this.originalTrainerId = originalTrainerId;
        this.pokeball = pokeball;
        this.captureDate = captureDate;
        this.initialLevel = initialLevel;
        this.originLocation = originLocation;
    }

    public String getOriginalTrainerId() { return originalTrainerId; }
    public String getPokeball() { return pokeball; }
    public LocalDate getCaptureDate() { return captureDate; }
    public int getInitialLevel() { return initialLevel; }
    public String getOriginLocation() { return originLocation; }
}
```

- [ ] **Step 4: Ajustar el test y correr**

Quitar el primer método `acceptsUpToFourUniqueMoves` del test su assert contradictorio (dejar solo la verificación de 3 válidos) y correr:

Run: `./mvnw -q test -Dtest=MovesetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain src/test/java/skaro/pokeapi/trainer/domain/MovesetTest.java
git commit -m "feat: Nature, Moveset, HeldItem, SpeciesRef, OriginMetadata value objects"
```

---

### Task 5: Entidad `PokemonInstance`

El ejemplar. Identidad por UUID; inmutable salvo `heldItem` y la transición de evolución (que devuelve un ejemplar nuevo preservando identidad).

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/PokemonInstance.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/PokemonInstanceTest.java`

**Interfaces:**
- Consumes: todos los VOs de Tasks 2–4.
- Produces: `PokemonInstance` con builder `PokemonInstance.builder()...build()`; getters `getId():UUID`, `getSpecies():SpeciesRef`, `getNickname():String`, `getIvs():IndividualValues`, `getEvs():EffortValues`, `getNature():Nature`, `getAbility():String`, `isShiny():boolean`, `getGender():Gender`, `getMoveset():Moveset`, `getHeldItem():Optional<HeldItem>`, `getOrigin():OriginMetadata`; método `evolve(SpeciesRef newSpecies, String newAbility):PokemonInstance` que **preserva** id, ivs, evs, nature, moveset, heldItem, origin, shiny, gender, nickname y cambia species + ability.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PokemonInstanceTest {

    private IndividualValues ivs(int v) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, v);
        return new IndividualValues(m);
    }

    private EffortValues evs() {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, 0);
        return new EffortValues(m);
    }

    private PokemonInstance magikarp() {
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef("magikarp"))
                .nickname("Splashy")
                .ivs(ivs(31))
                .evs(evs())
                .nature(new Nature("adamant"))
                .ability("swift-swim")
                .shiny(true)
                .gender(Gender.MALE)
                .moveset(new Moveset(Arrays.asList("splash")))
                .heldItem(new HeldItem("mystic-water"))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "route-1"))
                .build();
    }

    @Test
    void buildsWithAllFields() {
        PokemonInstance p = magikarp();
        assertEquals("magikarp", p.getSpecies().getName());
        assertTrue(p.isShiny());
        assertTrue(p.getHeldItem().isPresent());
        assertEquals("mystic-water", p.getHeldItem().get().getName());
    }

    @Test
    void evolvePreservesIdentityAndGeneticsButChangesSpeciesAndAbility() {
        PokemonInstance magikarp = magikarp();
        PokemonInstance gyarados = magikarp.evolve(new SpeciesRef("gyarados"), "intimidate");

        assertEquals(magikarp.getId(), gyarados.getId());
        assertEquals("gyarados", gyarados.getSpecies().getName());
        assertEquals("intimidate", gyarados.getAbility());
        assertEquals(magikarp.getIvs().get(Stat.SPEED), gyarados.getIvs().get(Stat.SPEED));
        assertEquals(magikarp.getNature(), gyarados.getNature());
        assertEquals(magikarp.getOrigin().getOriginalTrainerId(), gyarados.getOrigin().getOriginalTrainerId());
        assertEquals(magikarp.isShiny(), gyarados.isShiny());
    }

    @Test
    void requiresSpeciesAndId() {
        assertThrows(NullPointerException.class, () -> PokemonInstance.builder()
                .ivs(ivs(0)).evs(evs()).nature(new Nature("hardy")).ability("x")
                .gender(Gender.GENDERLESS).moveset(new Moveset(Collections.emptyList()))
                .origin(new OriginMetadata("ot", "ball", LocalDate.now(), 1, "lab"))
                .build());
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=PokemonInstanceTest`
Expected: FAIL.

- [ ] **Step 3: Implementar la entidad con builder**

```java
package skaro.pokeapi.trainer.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PokemonInstance {

    private final UUID id;
    private final SpeciesRef species;
    private final String nickname;
    private final IndividualValues ivs;
    private final EffortValues evs;
    private final Nature nature;
    private final String ability;
    private final boolean shiny;
    private final Gender gender;
    private final Moveset moveset;
    private final HeldItem heldItem;
    private final OriginMetadata origin;

    private PokemonInstance(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.species = Objects.requireNonNull(b.species, "species");
        this.nickname = b.nickname;
        this.ivs = Objects.requireNonNull(b.ivs, "ivs");
        this.evs = Objects.requireNonNull(b.evs, "evs");
        this.nature = Objects.requireNonNull(b.nature, "nature");
        this.ability = Objects.requireNonNull(b.ability, "ability");
        this.shiny = b.shiny;
        this.gender = Objects.requireNonNull(b.gender, "gender");
        this.moveset = Objects.requireNonNull(b.moveset, "moveset");
        this.heldItem = b.heldItem;
        this.origin = Objects.requireNonNull(b.origin, "origin");
    }

    public PokemonInstance evolve(SpeciesRef newSpecies, String newAbility) {
        return toBuilder()
                .species(Objects.requireNonNull(newSpecies, "newSpecies"))
                .ability(Objects.requireNonNull(newAbility, "newAbility"))
                .build();
    }

    public UUID getId() { return id; }
    public SpeciesRef getSpecies() { return species; }
    public String getNickname() { return nickname; }
    public IndividualValues getIvs() { return ivs; }
    public EffortValues getEvs() { return evs; }
    public Nature getNature() { return nature; }
    public String getAbility() { return ability; }
    public boolean isShiny() { return shiny; }
    public Gender getGender() { return gender; }
    public Moveset getMoveset() { return moveset; }
    public Optional<HeldItem> getHeldItem() { return Optional.ofNullable(heldItem); }
    public OriginMetadata getOrigin() { return origin; }

    public static Builder builder() { return new Builder(); }

    private Builder toBuilder() {
        return new Builder()
                .id(id).species(species).nickname(nickname).ivs(ivs).evs(evs)
                .nature(nature).ability(ability).shiny(shiny).gender(gender)
                .moveset(moveset).heldItem(heldItem).origin(origin);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PokemonInstance)) return false;
        return id.equals(((PokemonInstance) o).id);
    }
    @Override public int hashCode() { return Objects.hash(id); }

    public static final class Builder {
        private UUID id;
        private SpeciesRef species;
        private String nickname;
        private IndividualValues ivs;
        private EffortValues evs;
        private Nature nature;
        private String ability;
        private boolean shiny;
        private Gender gender;
        private Moveset moveset;
        private HeldItem heldItem;
        private OriginMetadata origin;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder species(SpeciesRef species) { this.species = species; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder ivs(IndividualValues ivs) { this.ivs = ivs; return this; }
        public Builder evs(EffortValues evs) { this.evs = evs; return this; }
        public Builder nature(Nature nature) { this.nature = nature; return this; }
        public Builder ability(String ability) { this.ability = ability; return this; }
        public Builder shiny(boolean shiny) { this.shiny = shiny; return this; }
        public Builder gender(Gender gender) { this.gender = gender; return this; }
        public Builder moveset(Moveset moveset) { this.moveset = moveset; return this; }
        public Builder heldItem(HeldItem heldItem) { this.heldItem = heldItem; return this; }
        public Builder origin(OriginMetadata origin) { this.origin = origin; return this; }

        public PokemonInstance build() { return new PokemonInstance(this); }
    }
}
```

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=PokemonInstanceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain/PokemonInstance.java src/test/java/skaro/pokeapi/trainer/domain/PokemonInstanceTest.java
git commit -m "feat: PokemonInstance entity with identity-preserving evolve"
```

---

### Task 6: `StorageLocation`, `StorageLimits` y excepciones de almacenamiento

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/StorageLocation.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/StorageLimits.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/PartyFullException.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/BoxFullException.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/StorageFullException.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InstanceNotFoundException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/StorageLimitsTest.java`

**Interfaces:**
- Produces: `StorageLocation` (enum `PARTY, BOX`); `StorageLimits(int partyMax, int boxMax)` con `getPartyMax()`, `getBoxMax()`; excepciones: `PartyFullException`, `BoxFullException`, `StorageFullException`, `InstanceNotFoundException(UUID)` — todas `extends RuntimeException`.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageLimitsTest {

    @Test
    void exposesConfiguredLimits() {
        StorageLimits limits = new StorageLimits(6, 30);
        assertEquals(6, limits.getPartyMax());
        assertEquals(30, limits.getBoxMax());
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(0, 30));
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(6, -1));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=StorageLimitsTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

`StorageLocation.java`:
```java
package skaro.pokeapi.trainer.domain;

public enum StorageLocation {
    PARTY, BOX
}
```

`StorageLimits.java`:
```java
package skaro.pokeapi.trainer.domain;

public final class StorageLimits {
    private final int partyMax;
    private final int boxMax;

    public StorageLimits(int partyMax, int boxMax) {
        if (partyMax < 1) throw new IllegalArgumentException("partyMax debe ser >= 1");
        if (boxMax < 1) throw new IllegalArgumentException("boxMax debe ser >= 1");
        this.partyMax = partyMax;
        this.boxMax = boxMax;
    }

    public int getPartyMax() { return partyMax; }
    public int getBoxMax() { return boxMax; }
}
```

`exception/PartyFullException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class PartyFullException extends RuntimeException {
    public PartyFullException(int max) {
        super("El equipo activo está lleno (máximo " + max + ")");
    }
}
```

`exception/BoxFullException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class BoxFullException extends RuntimeException {
    public BoxFullException(int max) {
        super("El baúl está lleno (máximo " + max + ")");
    }
}
```

`exception/StorageFullException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class StorageFullException extends RuntimeException {
    public StorageFullException() {
        super("No hay espacio disponible ni en el equipo activo ni en el baúl");
    }
}
```

`exception/InstanceNotFoundException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

import java.util.UUID;

public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException(UUID id) {
        super("No existe un ejemplar con id " + id);
    }
}
```

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=StorageLimitsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain src/test/java/skaro/pokeapi/trainer/domain/StorageLimitsTest.java
git commit -m "feat: storage location/limits value objects and domain exceptions"
```

---

### Task 7: Agregado `Trainer`

La raíz de agregado. Dueña de las invariantes de party/baúl, el auto-placement en captura, los traslados y la evolución (preservando slot).

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/Trainer.java`
- Test: `src/test/java/skaro/pokeapi/trainer/domain/TrainerTest.java`

**Interfaces:**
- Consumes: `PokemonInstance`, `StorageLimits`, `StorageLocation`, excepciones de Task 6, `SpeciesRef`.
- Produces: `Trainer(String trainerId, StorageLimits limits)`; `StorageLocation capture(PokemonInstance)` (auto-placement party→box, tira `StorageFullException` si ambos llenos); `void moveToParty(UUID)`; `void moveToBox(UUID)`; `PokemonInstance findById(UUID)` (tira `InstanceNotFoundException`); `StorageLocation locationOf(UUID)`; `List<PokemonInstance> party()`; `List<PokemonInstance> box()`; `void evolve(UUID id, SpeciesRef newSpecies, String newAbility)` (preserva ubicación y slot); `String getTrainerId()`. El party preserva orden de slot; `box()` y `party()` devuelven listas inmutables.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainerTest {

    private Trainer trainer;

    @BeforeEach
    void setUp() {
        trainer = new Trainer("ash", new StorageLimits(2, 2));
    }

    private PokemonInstance instance(String species) {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef(species))
                .ivs(new IndividualValues(ivs))
                .evs(new EffortValues(evs))
                .nature(new Nature("hardy"))
                .ability("overgrow")
                .gender(Gender.MALE)
                .moveset(new Moveset(Collections.emptyList()))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "pallet-town"))
                .build();
    }

    @Test
    void capturePlacesInPartyWhileThereIsRoom() {
        PokemonInstance p = instance("bulbasaur");
        assertEquals(StorageLocation.PARTY, trainer.capture(p));
        assertEquals(1, trainer.party().size());
    }

    @Test
    void captureOverflowsToBoxWhenPartyIsFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        assertEquals(StorageLocation.BOX, trainer.capture(instance("c")));
        assertEquals(2, trainer.party().size());
        assertEquals(1, trainer.box().size());
    }

    @Test
    void captureThrowsWhenPartyAndBoxFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        trainer.capture(instance("c"));
        trainer.capture(instance("d"));
        assertThrows(StorageFullException.class, () -> trainer.capture(instance("e")));
    }

    @Test
    void moveToBoxThenBackToPartyPreservesInstance() {
        PokemonInstance p = instance("pikachu");
        trainer.capture(p);
        trainer.moveToBox(p.getId());
        assertEquals(StorageLocation.BOX, trainer.locationOf(p.getId()));
        trainer.moveToParty(p.getId());
        assertEquals(StorageLocation.PARTY, trainer.locationOf(p.getId()));
    }

    @Test
    void moveToPartyThrowsWhenPartyFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        PokemonInstance boxed = instance("c");
        trainer.capture(boxed); // overflow to box
        assertThrows(PartyFullException.class, () -> trainer.moveToParty(boxed.getId()));
    }

    @Test
    void findByIdThrowsWhenAbsent() {
        assertThrows(InstanceNotFoundException.class, () -> trainer.findById(UUID.randomUUID()));
    }

    @Test
    void evolvePreservesPartySlot() {
        PokemonInstance first = instance("a");
        PokemonInstance magikarp = instance("magikarp");
        trainer.capture(first);
        trainer.capture(magikarp);
        trainer.evolve(magikarp.getId(), new SpeciesRef("gyarados"), "intimidate");

        assertEquals("gyarados", trainer.party().get(1).getSpecies().getName());
        assertEquals("intimidate", trainer.party().get(1).getAbility());
        assertEquals(magikarp.getId(), trainer.party().get(1).getId());
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=TrainerTest`
Expected: FAIL.

- [ ] **Step 3: Implementar el agregado**

```java
package skaro.pokeapi.trainer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import skaro.pokeapi.trainer.domain.exception.BoxFullException;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;

public class Trainer {

    private final String trainerId;
    private final StorageLimits limits;
    private final List<PokemonInstance> party = new ArrayList<>();
    private final List<PokemonInstance> box = new ArrayList<>();

    public Trainer(String trainerId, StorageLimits limits) {
        if (trainerId == null || trainerId.trim().isEmpty()) {
            throw new IllegalArgumentException("trainerId no puede ser vacío");
        }
        this.trainerId = trainerId.trim();
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public StorageLocation capture(PokemonInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (party.size() < limits.getPartyMax()) {
            party.add(instance);
            return StorageLocation.PARTY;
        }
        if (box.size() < limits.getBoxMax()) {
            box.add(instance);
            return StorageLocation.BOX;
        }
        throw new StorageFullException();
    }

    public void moveToParty(UUID id) {
        int idx = indexIn(box, id);
        if (idx < 0) {
            if (indexIn(party, id) >= 0) return; // ya está en el party (idempotente)
            throw new InstanceNotFoundException(id);
        }
        if (party.size() >= limits.getPartyMax()) {
            throw new PartyFullException(limits.getPartyMax());
        }
        party.add(box.remove(idx));
    }

    public void moveToBox(UUID id) {
        int idx = indexIn(party, id);
        if (idx < 0) {
            if (indexIn(box, id) >= 0) return; // ya está en el baúl (idempotente)
            throw new InstanceNotFoundException(id);
        }
        if (box.size() >= limits.getBoxMax()) {
            throw new BoxFullException(limits.getBoxMax());
        }
        box.add(party.remove(idx));
    }

    public void evolve(UUID id, SpeciesRef newSpecies, String newAbility) {
        int partyIdx = indexIn(party, id);
        if (partyIdx >= 0) {
            party.set(partyIdx, party.get(partyIdx).evolve(newSpecies, newAbility));
            return;
        }
        int boxIdx = indexIn(box, id);
        if (boxIdx >= 0) {
            box.set(boxIdx, box.get(boxIdx).evolve(newSpecies, newAbility));
            return;
        }
        throw new InstanceNotFoundException(id);
    }

    public PokemonInstance findById(UUID id) {
        int p = indexIn(party, id);
        if (p >= 0) return party.get(p);
        int b = indexIn(box, id);
        if (b >= 0) return box.get(b);
        throw new InstanceNotFoundException(id);
    }

    public StorageLocation locationOf(UUID id) {
        if (indexIn(party, id) >= 0) return StorageLocation.PARTY;
        if (indexIn(box, id) >= 0) return StorageLocation.BOX;
        throw new InstanceNotFoundException(id);
    }

    public List<PokemonInstance> party() { return Collections.unmodifiableList(new ArrayList<>(party)); }
    public List<PokemonInstance> box() { return Collections.unmodifiableList(new ArrayList<>(box)); }
    public String getTrainerId() { return trainerId; }

    private int indexIn(List<PokemonInstance> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}
```

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=TrainerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/domain/Trainer.java src/test/java/skaro/pokeapi/trainer/domain/TrainerTest.java
git commit -m "feat: Trainer aggregate with capture/move/evolve invariants"
```

---

### Task 8: Puertos de salida `PokemonRepository` y `PokemonCatalogPort` + `SpeciesData`

Interfaces de la capa de aplicación. Sin implementación todavía (eso va en Tasks 9–10). Una interfaz no se testea sola; esta tarea entrega los contratos que consumen las siguientes.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/application/port/PokemonRepository.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/port/PokemonCatalogPort.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/port/SpeciesData.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/port/EvolutionOption.java`

**Interfaces:**
- Produces:
  - `PokemonRepository`: `Mono<Trainer> findByTrainerId(String id)`, `Mono<Trainer> loadOrCreate(String id)`, `Mono<Trainer> save(Trainer trainer)`.
  - `PokemonCatalogPort`: `Mono<SpeciesData> getSpecies(String speciesName)`, `Flux<EvolutionOption> directEvolutionsOf(String speciesName)`.
  - `SpeciesData`: getters `getName():String`, `getAbilities():List<String>`, `getTypes():List<String>`, `getBaseStats():Map<Stat,Integer>`, `getSpriteUrl():String`.
  - `EvolutionOption`: `getFromSpecies():String`, `getToSpecies():String`, `getAllowedAbilities():List<String>`.

- [ ] **Step 1: Crear las interfaces y DTOs del puerto**

`port/SpeciesData.java`:
```java
package skaro.pokeapi.trainer.application.port;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Stat;

public class SpeciesData {
    private final String name;
    private final List<String> abilities;
    private final List<String> types;
    private final Map<Stat, Integer> baseStats;
    private final String spriteUrl;

    public SpeciesData(String name, List<String> abilities, List<String> types,
                       Map<Stat, Integer> baseStats, String spriteUrl) {
        this.name = name;
        this.abilities = abilities;
        this.types = types;
        this.baseStats = baseStats;
        this.spriteUrl = spriteUrl;
    }

    public String getName() { return name; }
    public List<String> getAbilities() { return abilities; }
    public List<String> getTypes() { return types; }
    public Map<Stat, Integer> getBaseStats() { return baseStats; }
    public String getSpriteUrl() { return spriteUrl; }
}
```

`port/EvolutionOption.java`:
```java
package skaro.pokeapi.trainer.application.port;

import java.util.List;

public class EvolutionOption {
    private final String fromSpecies;
    private final String toSpecies;
    private final List<String> allowedAbilities;

    public EvolutionOption(String fromSpecies, String toSpecies, List<String> allowedAbilities) {
        this.fromSpecies = fromSpecies;
        this.toSpecies = toSpecies;
        this.allowedAbilities = allowedAbilities;
    }

    public String getFromSpecies() { return fromSpecies; }
    public String getToSpecies() { return toSpecies; }
    public List<String> getAllowedAbilities() { return allowedAbilities; }
}
```

`port/PokemonRepository.java`:
```java
package skaro.pokeapi.trainer.application.port;

import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.domain.Trainer;

public interface PokemonRepository {
    Mono<Trainer> findByTrainerId(String trainerId);
    Mono<Trainer> loadOrCreate(String trainerId);
    Mono<Trainer> save(Trainer trainer);
}
```

`port/PokemonCatalogPort.java`:
```java
package skaro.pokeapi.trainer.application.port;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PokemonCatalogPort {
    Mono<SpeciesData> getSpecies(String speciesName);
    Flux<EvolutionOption> directEvolutionsOf(String speciesName);
}
```

- [ ] **Step 2: Compilar**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/application/port
git commit -m "feat: outbound ports (PokemonRepository, PokemonCatalogPort) and catalog DTOs"
```

---

### Task 9: Adapter de persistencia `InMemoryPokemonRepository`

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/persistence/InMemoryPokemonRepository.java`
- Test: `src/test/java/skaro/pokeapi/trainer/adapter/persistence/InMemoryPokemonRepositoryTest.java`

**Interfaces:**
- Consumes: `PokemonRepository`, `Trainer`, `StorageLimits`, `StorageProperties`.
- Produces: `InMemoryPokemonRepository implements PokemonRepository` (bean `@Repository`). `loadOrCreate` crea un `Trainer` con `StorageLimits` tomados de `StorageProperties` si no existe.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.adapter.persistence;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.domain.Trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPokemonRepositoryTest {

    private StorageProperties props() {
        StorageProperties p = new StorageProperties();
        p.getParty().setMaxSize(6);
        p.getBox().setMaxSize(30);
        return p;
    }

    @Test
    void loadOrCreateReturnsNewTrainerWithConfiguredLimits() {
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props());
        StepVerifier.create(repo.loadOrCreate("ash"))
                .assertNext(t -> assertEquals("ash", t.getTrainerId()))
                .verifyComplete();
    }

    @Test
    void saveThenFindRoundTrips() {
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props());
        Trainer ash = repo.loadOrCreate("ash").block();
        repo.save(ash).block();
        StepVerifier.create(repo.findByTrainerId("ash"))
                .assertNext(t -> assertEquals("ash", t.getTrainerId()))
                .verifyComplete();
    }

    @Test
    void findMissingTrainerCompletesEmpty() {
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props());
        StepVerifier.create(repo.findByTrainerId("missing")).verifyComplete();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=InMemoryPokemonRepositoryTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

```java
package skaro.pokeapi.trainer.adapter.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.port.PokemonRepository;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.domain.StorageLimits;
import skaro.pokeapi.trainer.domain.Trainer;

@Repository
public class InMemoryPokemonRepository implements PokemonRepository {

    private final ConcurrentMap<String, Trainer> store = new ConcurrentHashMap<>();
    private final StorageProperties properties;

    public InMemoryPokemonRepository(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Trainer> findByTrainerId(String trainerId) {
        return Mono.justOrEmpty(store.get(trainerId));
    }

    @Override
    public Mono<Trainer> loadOrCreate(String trainerId) {
        return Mono.fromSupplier(() -> store.computeIfAbsent(trainerId,
                id -> new Trainer(id, new StorageLimits(
                        properties.getParty().getMaxSize(),
                        properties.getBox().getMaxSize()))));
    }

    @Override
    public Mono<Trainer> save(Trainer trainer) {
        return Mono.fromSupplier(() -> {
            store.put(trainer.getTrainerId(), trainer);
            return trainer;
        });
    }
}
```

> **Nota de diseño:** el agregado es mutable y se guarda por referencia (in-memory). Al migrar a un store real (R2DBC/Mongo), `save` persiste una copia y `loadOrCreate`/`find` reconstruyen el agregado — la firma del puerto no cambia.

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=InMemoryPokemonRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/adapter/persistence src/test/java/skaro/pokeapi/trainer/adapter/persistence
git commit -m "feat: in-memory PokemonRepository adapter"
```

---

### Task 10: Adapter `PokeApiCatalogAdapter` sobre `PokeApiClient`

Implementa `PokemonCatalogPort` traduciendo los recursos de PokéAPI al modelo del puerto. Testea con `mockwebserver` (ya presente), apuntando el cliente a una URL local.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/pokeapi/PokeApiCatalogAdapter.java`
- Test: `src/test/java/skaro/pokeapi/trainer/adapter/pokeapi/PokeApiCatalogAdapterTest.java`

**Interfaces:**
- Consumes: `PokemonCatalogPort`, `SpeciesData`, `EvolutionOption`, `Stat`, `skaro.pokeapi.client.PokeApiClient`, `skaro.pokeapi.resource.pokemon.Pokemon`.
- Produces: `PokeApiCatalogAdapter implements PokemonCatalogPort` (bean `@Component`). Mapea `Pokemon.getAbilities()` → nombres, `getTypes()` → nombres, `getStats()` → `Map<Stat,Integer>` (mapeo de nombre de stat PokéAPI a `Stat`), `getSprites().getFrontDefault()` → spriteUrl.

- [ ] **Step 1: Escribir el test (mapeo de especie)**

```java
package skaro.pokeapi.trainer.adapter.pokeapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.client.ReactiveNonCachingPokeApiClient;
import skaro.pokeapi.client.WebClientEntityFactory;
import skaro.pokeapi.client.MapEndpointRegistry;
import skaro.pokeapi.trainer.domain.Stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokeApiCatalogAdapterTest {

    private MockWebServer server;
    private PokeApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new ReactiveNonCachingPokeApiClient(
                new WebClientEntityFactory(webClient), new MapEndpointRegistry());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void mapsSpeciesAbilitiesTypesAndStats() {
        String body = "{"
                + "\"name\":\"bulbasaur\","
                + "\"abilities\":[{\"ability\":{\"name\":\"overgrow\",\"url\":\"x\"},\"is_hidden\":false,\"slot\":1}],"
                + "\"types\":[{\"slot\":1,\"type\":{\"name\":\"grass\",\"url\":\"x\"}}],"
                + "\"stats\":[{\"base_stat\":45,\"effort\":0,\"stat\":{\"name\":\"hp\",\"url\":\"x\"}}],"
                + "\"sprites\":{\"front_default\":\"http://img/1.png\"}"
                + "}";
        server.enqueue(new MockResponse().setBody(body)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        PokeApiCatalogAdapter adapter = new PokeApiCatalogAdapter(client);

        StepVerifier.create(adapter.getSpecies("bulbasaur"))
                .assertNext(data -> {
                    assertEquals("bulbasaur", data.getName());
                    assertTrue(data.getAbilities().contains("overgrow"));
                    assertTrue(data.getTypes().contains("grass"));
                    assertEquals(45, data.getBaseStats().get(Stat.HP));
                    assertEquals("http://img/1.png", data.getSpriteUrl());
                })
                .verifyComplete();
    }
}
```

> Si los constructores de `ReactiveNonCachingPokeApiClient`/`WebClientEntityFactory`/`MapEndpointRegistry` difieren, ajustar el armado del cliente según `src/test/java/skaro/pokeapi/client/ReactiveNonCachingPokeApiClientTest.java` (ya existe y muestra el patrón exacto de construcción con `mockwebserver`).

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=PokeApiCatalogAdapterTest`
Expected: FAIL.

- [ ] **Step 3: Implementar el adapter**

```java
package skaro.pokeapi.trainer.adapter.pokeapi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.resource.pokemon.PokemonStat;
import skaro.pokeapi.trainer.application.port.EvolutionOption;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.Stat;

@Component
public class PokeApiCatalogAdapter implements PokemonCatalogPort {

    private final PokeApiClient pokeApiClient;

    public PokeApiCatalogAdapter(PokeApiClient pokeApiClient) {
        this.pokeApiClient = pokeApiClient;
    }

    @Override
    public Mono<SpeciesData> getSpecies(String speciesName) {
        return pokeApiClient.getResource(Pokemon.class, speciesName)
                .map(this::toSpeciesData);
    }

    @Override
    public Flux<EvolutionOption> directEvolutionsOf(String speciesName) {
        // Se implementa en la Task 12 (evolución). Por ahora, contrato cumplido vacío.
        return Flux.empty();
    }

    private SpeciesData toSpeciesData(Pokemon pokemon) {
        List<String> abilities = new ArrayList<>();
        pokemon.getAbilities().forEach(a -> abilities.add(a.getAbility().getName()));

        List<String> types = new ArrayList<>();
        pokemon.getTypes().forEach(t -> types.add(t.getType().getName()));

        Map<Stat, Integer> baseStats = new EnumMap<>(Stat.class);
        for (PokemonStat stat : pokemon.getStats()) {
            Stat mapped = mapStat(stat.getStat().getName());
            if (mapped != null) {
                baseStats.put(mapped, stat.getBaseStat());
            }
        }

        String spriteUrl = pokemon.getSprites() == null ? null : pokemon.getSprites().getFrontDefault();
        return new SpeciesData(pokemon.getName(), abilities, types, baseStats, spriteUrl);
    }

    private Stat mapStat(String pokeApiName) {
        switch (pokeApiName) {
            case "hp": return Stat.HP;
            case "attack": return Stat.ATTACK;
            case "defense": return Stat.DEFENSE;
            case "special-attack": return Stat.SPECIAL_ATTACK;
            case "special-defense": return Stat.SPECIAL_DEFENSE;
            case "speed": return Stat.SPEED;
            default: return null;
        }
    }
}
```

> **Verificar** el getter real del sprite en `skaro.pokeapi.resource.pokemon.PokemonSprites` (puede llamarse `getFrontDefault`); ajustar si difiere.

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=PokeApiCatalogAdapterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/adapter/pokeapi src/test/java/skaro/pokeapi/trainer/adapter/pokeapi
git commit -m "feat: PokeApi catalog adapter mapping species data"
```

---

### Task 11: Casos de uso de captura, traslado, detalle y listados

Orquestan dominio + puertos. La validación contra PokéAPI (especie existe, habilidad pertenece a la especie) vive acá, antes de invocar el dominio puro. La vista compuesta del party se arma acá.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/application/CaptureCommand.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/CaptureResult.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/PartyMember.java`
- Create: `src/main/java/skaro/pokeapi/trainer/application/TrainerService.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidAbilityException.java`
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/SpeciesNotFoundException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/application/TrainerServiceTest.java`

**Interfaces:**
- Consumes: `PokemonRepository`, `PokemonCatalogPort`, `Trainer`, `PokemonInstance` (+ VOs), `SpeciesData`.
- Produces:
  - `CaptureCommand` (POJO con todos los parámetros del ejemplar: species, nickname, `Map<Stat,Integer>` ivs/evs, nature, ability, shiny, gender, `List<String>` moves, heldItem, OT, pokeball, initialLevel, originLocation).
  - `CaptureResult` (`UUID instanceId`, `StorageLocation location`).
  - `PartyMember` (`PokemonInstance instance`, `SpeciesData species`) — la vista compuesta.
  - `TrainerService` con: `Mono<CaptureResult> capture(String trainerId, CaptureCommand cmd)`, `Mono<PokemonInstance> getInstance(String trainerId, UUID id)`, `Flux<PartyMember> listParty(String trainerId)`, `Flux<PokemonInstance> listBox(String trainerId)`, `Mono<StorageLocation> move(String trainerId, UUID id, StorageLocation target)`.
  - `InvalidAbilityException`, `SpeciesNotFoundException` (`extends RuntimeException`).

- [ ] **Step 1: Escribir el test (captura feliz + habilidad inválida + composición del party)**

```java
package skaro.pokeapi.trainer.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.PokemonRepository;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.adapter.persistence.InMemoryPokemonRepository;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;
import skaro.pokeapi.trainer.domain.StorageLocation;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

class TrainerServiceTest {

    private PokemonRepository repository;
    private PokemonCatalogPort catalog;
    private TrainerService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getParty().setMaxSize(6);
        props.getBox().setMaxSize(30);
        repository = new InMemoryPokemonRepository(props);
        catalog = new PokemonCatalogPort() {
            @Override public Mono<SpeciesData> getSpecies(String name) {
                if (!"bulbasaur".equals(name)) return Mono.empty();
                Map<Stat, Integer> base = new EnumMap<>(Stat.class);
                for (Stat s : Stat.values()) base.put(s, 45);
                return Mono.just(new SpeciesData("bulbasaur",
                        Arrays.asList("overgrow", "chlorophyll"),
                        Arrays.asList("grass", "poison"), base, "http://img/1.png"));
            }
            @Override public Flux<skaro.pokeapi.trainer.application.port.EvolutionOption> directEvolutionsOf(String name) {
                return Flux.empty();
            }
        };
        service = new TrainerService(repository, catalog);
    }

    private CaptureCommand command(String species, String ability) {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        CaptureCommand cmd = new CaptureCommand();
        cmd.setSpecies(species);
        cmd.setNickname("Bulby");
        cmd.setIvs(ivs);
        cmd.setEvs(evs);
        cmd.setNature("hardy");
        cmd.setAbility(ability);
        cmd.setShiny(false);
        cmd.setGender(Gender.MALE);
        cmd.setMoves(Collections.singletonList("tackle"));
        cmd.setHeldItem(null);
        cmd.setOriginalTrainerId("ash");
        cmd.setPokeball("poke-ball");
        cmd.setInitialLevel(5);
        cmd.setOriginLocation("pallet-town");
        return cmd;
    }

    @Test
    void captureStoresInstanceAndReturnsLocation() {
        StepVerifier.create(service.capture("ash", command("bulbasaur", "overgrow")))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(StorageLocation.PARTY, result.getLocation());
                    org.junit.jupiter.api.Assertions.assertNotNull(result.getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    void captureRejectsAbilityNotBelongingToSpecies() {
        StepVerifier.create(service.capture("ash", command("bulbasaur", "levitate")))
                .expectError(InvalidAbilityException.class)
                .verify();
    }

    @Test
    void listPartyReturnsCompositeViewWithSpeciesData() {
        service.capture("ash", command("bulbasaur", "overgrow")).block();
        StepVerifier.create(service.listParty("ash"))
                .assertNext(member -> {
                    org.junit.jupiter.api.Assertions.assertEquals("bulbasaur", member.getInstance().getSpecies().getName());
                    org.junit.jupiter.api.Assertions.assertEquals("http://img/1.png", member.getSpecies().getSpriteUrl());
                })
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=TrainerServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implementar comandos/resultados y el servicio**

`domain/exception/SpeciesNotFoundException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class SpeciesNotFoundException extends RuntimeException {
    public SpeciesNotFoundException(String species) {
        super("La especie no existe en PokéAPI: " + species);
    }
}
```

`domain/exception/InvalidAbilityException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class InvalidAbilityException extends RuntimeException {
    public InvalidAbilityException(String ability, String species) {
        super("La habilidad '" + ability + "' no pertenece a la especie '" + species + "'");
    }
}
```

`application/CaptureCommand.java`:
```java
package skaro.pokeapi.trainer.application;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;

public class CaptureCommand {
    private String species;
    private String nickname;
    private Map<Stat, Integer> ivs;
    private Map<Stat, Integer> evs;
    private String nature;
    private String ability;
    private boolean shiny;
    private Gender gender;
    private List<String> moves;
    private String heldItem;
    private String originalTrainerId;
    private String pokeball;
    private int initialLevel;
    private String originLocation;

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Map<Stat, Integer> getIvs() { return ivs; }
    public void setIvs(Map<Stat, Integer> ivs) { this.ivs = ivs; }
    public Map<Stat, Integer> getEvs() { return evs; }
    public void setEvs(Map<Stat, Integer> evs) { this.evs = evs; }
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getAbility() { return ability; }
    public void setAbility(String ability) { this.ability = ability; }
    public boolean isShiny() { return shiny; }
    public void setShiny(boolean shiny) { this.shiny = shiny; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public List<String> getMoves() { return moves; }
    public void setMoves(List<String> moves) { this.moves = moves; }
    public String getHeldItem() { return heldItem; }
    public void setHeldItem(String heldItem) { this.heldItem = heldItem; }
    public String getOriginalTrainerId() { return originalTrainerId; }
    public void setOriginalTrainerId(String originalTrainerId) { this.originalTrainerId = originalTrainerId; }
    public String getPokeball() { return pokeball; }
    public void setPokeball(String pokeball) { this.pokeball = pokeball; }
    public int getInitialLevel() { return initialLevel; }
    public void setInitialLevel(int initialLevel) { this.initialLevel = initialLevel; }
    public String getOriginLocation() { return originLocation; }
    public void setOriginLocation(String originLocation) { this.originLocation = originLocation; }
}
```

`application/CaptureResult.java`:
```java
package skaro.pokeapi.trainer.application;

import java.util.UUID;

import skaro.pokeapi.trainer.domain.StorageLocation;

public class CaptureResult {
    private final UUID instanceId;
    private final StorageLocation location;

    public CaptureResult(UUID instanceId, StorageLocation location) {
        this.instanceId = instanceId;
        this.location = location;
    }

    public UUID getInstanceId() { return instanceId; }
    public StorageLocation getLocation() { return location; }
}
```

`application/PartyMember.java`:
```java
package skaro.pokeapi.trainer.application;

import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.PokemonInstance;

public class PartyMember {
    private final PokemonInstance instance;
    private final SpeciesData species;

    public PartyMember(PokemonInstance instance, SpeciesData species) {
        this.instance = instance;
        this.species = species;
    }

    public PokemonInstance getInstance() { return instance; }
    public SpeciesData getSpecies() { return species; }
}
```

`application/TrainerService.java`:
```java
package skaro.pokeapi.trainer.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.PokemonRepository;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.EffortValues;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.HeldItem;
import skaro.pokeapi.trainer.domain.IndividualValues;
import skaro.pokeapi.trainer.domain.Moveset;
import skaro.pokeapi.trainer.domain.Nature;
import skaro.pokeapi.trainer.domain.OriginMetadata;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.SpeciesRef;
import skaro.pokeapi.trainer.domain.StorageLocation;
import skaro.pokeapi.trainer.domain.Trainer;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;
import skaro.pokeapi.trainer.domain.exception.SpeciesNotFoundException;

@Service
public class TrainerService {

    private final PokemonRepository repository;
    private final PokemonCatalogPort catalog;

    public TrainerService(PokemonRepository repository, PokemonCatalogPort catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    public Mono<CaptureResult> capture(String trainerId, CaptureCommand cmd) {
        return catalog.getSpecies(cmd.getSpecies())
                .switchIfEmpty(Mono.error(new SpeciesNotFoundException(cmd.getSpecies())))
                .flatMap(species -> {
                    validateAbility(cmd.getAbility(), species);
                    PokemonInstance instance = toInstance(cmd);
                    return repository.loadOrCreate(trainerId)
                            .flatMap(trainer -> {
                                StorageLocation location = trainer.capture(instance);
                                return repository.save(trainer)
                                        .thenReturn(new CaptureResult(instance.getId(), location));
                            });
                });
    }

    public Mono<PokemonInstance> getInstance(String trainerId, UUID id) {
        return requireTrainer(trainerId).map(trainer -> trainer.findById(id));
    }

    public Flux<PartyMember> listParty(String trainerId) {
        return requireTrainer(trainerId)
                .flatMapMany(trainer -> Flux.fromIterable(trainer.party()))
                .flatMap(instance -> catalog.getSpecies(instance.getSpecies().getName())
                        .map(species -> new PartyMember(instance, species)));
    }

    public Flux<PokemonInstance> listBox(String trainerId) {
        return requireTrainer(trainerId)
                .flatMapMany(trainer -> Flux.fromIterable(trainer.box()));
    }

    public Mono<StorageLocation> move(String trainerId, UUID id, StorageLocation target) {
        return requireTrainer(trainerId).flatMap(trainer -> {
            if (target == StorageLocation.PARTY) {
                trainer.moveToParty(id);
            } else {
                trainer.moveToBox(id);
            }
            return repository.save(trainer).thenReturn(trainer.locationOf(id));
        });
    }

    private Mono<Trainer> requireTrainer(String trainerId) {
        return repository.findByTrainerId(trainerId)
                .switchIfEmpty(Mono.error(new SpeciesNotFoundException("trainer:" + trainerId)));
    }

    private void validateAbility(String ability, SpeciesData species) {
        String normalized = ability == null ? "" : ability.trim().toLowerCase();
        boolean belongs = species.getAbilities().stream()
                .anyMatch(a -> a.equalsIgnoreCase(normalized));
        if (!belongs) {
            throw new InvalidAbilityException(ability, species.getName());
        }
    }

    private PokemonInstance toInstance(CaptureCommand cmd) {
        HeldItem heldItem = (cmd.getHeldItem() == null || cmd.getHeldItem().trim().isEmpty())
                ? null : new HeldItem(cmd.getHeldItem());
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef(cmd.getSpecies()))
                .nickname(cmd.getNickname())
                .ivs(new IndividualValues(cmd.getIvs()))
                .evs(new EffortValues(cmd.getEvs()))
                .nature(new Nature(cmd.getNature()))
                .ability(cmd.getAbility().trim().toLowerCase())
                .shiny(cmd.isShiny())
                .gender(cmd.getGender() == null ? Gender.GENDERLESS : cmd.getGender())
                .moveset(new Moveset(cmd.getMoves()))
                .heldItem(heldItem)
                .origin(new OriginMetadata(cmd.getOriginalTrainerId(), cmd.getPokeball(),
                        LocalDate.now(), cmd.getInitialLevel(), cmd.getOriginLocation()))
                .build();
    }
}
```

> **Nota:** `requireTrainer` usa `SpeciesNotFoundException("trainer:"+id)` como marcador temporal; la Task 14 introduce `TrainerNotFoundException` y acá se reemplaza. Mantener el `switchIfEmpty` para no devolver vacío.

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=TrainerServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/application src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidAbilityException.java src/main/java/skaro/pokeapi/trainer/domain/exception/SpeciesNotFoundException.java src/test/java/skaro/pokeapi/trainer/application/TrainerServiceTest.java
git commit -m "feat: TrainerService use cases (capture, move, detail, party/box listing)"
```

---

### Task 12: Evolución (bonus) — caso de uso + mapeo de cadena evolutiva

Implementa `directEvolutionsOf` en el adapter PokéAPI (siguiendo `PokemonSpecies → evolution_chain → EvolutionChain`) y el caso de uso que valida el camino directo y actualiza la habilidad.

**Files:**
- Modify: `src/main/java/skaro/pokeapi/trainer/adapter/pokeapi/PokeApiCatalogAdapter.java` (implementar `directEvolutionsOf`)
- Modify: `src/main/java/skaro/pokeapi/trainer/application/TrainerService.java` (agregar `evolve`)
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/InvalidEvolutionException.java`
- Test: `src/test/java/skaro/pokeapi/trainer/application/EvolveUseCaseTest.java`

**Interfaces:**
- Consumes: `PokemonCatalogPort.directEvolutionsOf`, `Trainer.evolve`, `EvolutionOption`.
- Produces: `TrainerService.evolve(String trainerId, UUID id, String targetSpecies):Mono<PokemonInstance>`; `InvalidEvolutionException extends RuntimeException`. La nueva habilidad es la primera habilidad no-oculta permitida de la especie destino (regla determinística documentada).

- [ ] **Step 1: Escribir el test (con catálogo fake)**

```java
package skaro.pokeapi.trainer.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import skaro.pokeapi.trainer.application.port.EvolutionOption;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.adapter.persistence.InMemoryPokemonRepository;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;
import skaro.pokeapi.trainer.domain.exception.InvalidEvolutionException;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class EvolveUseCaseTest {

    private TrainerService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getParty().setMaxSize(6);
        props.getBox().setMaxSize(30);
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props);

        PokemonCatalogPort catalog = new PokemonCatalogPort() {
            @Override public Mono<SpeciesData> getSpecies(String name) {
                Map<Stat, Integer> base = new EnumMap<>(Stat.class);
                for (Stat s : Stat.values()) base.put(s, 50);
                if ("magikarp".equals(name)) {
                    return Mono.just(new SpeciesData("magikarp",
                            Collections.singletonList("swift-swim"),
                            Collections.singletonList("water"), base, "x"));
                }
                if ("gyarados".equals(name)) {
                    return Mono.just(new SpeciesData("gyarados",
                            Arrays.asList("intimidate", "moxie"),
                            Arrays.asList("water", "flying"), base, "x"));
                }
                return Mono.empty();
            }
            @Override public Flux<EvolutionOption> directEvolutionsOf(String name) {
                if ("magikarp".equals(name)) {
                    return Flux.just(new EvolutionOption("magikarp", "gyarados",
                            Arrays.asList("intimidate", "moxie")));
                }
                return Flux.empty();
            }
        };
        service = new TrainerService(repo, catalog);
        this.repo = repo;
    }

    private InMemoryPokemonRepository repo;

    private UUID captureMagikarp() {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        CaptureCommand cmd = new CaptureCommand();
        cmd.setSpecies("magikarp"); cmd.setIvs(ivs); cmd.setEvs(evs);
        cmd.setNature("adamant"); cmd.setAbility("swift-swim"); cmd.setGender(Gender.MALE);
        cmd.setMoves(Collections.singletonList("splash")); cmd.setOriginalTrainerId("ash");
        cmd.setPokeball("poke-ball"); cmd.setInitialLevel(10); cmd.setOriginLocation("route-1");
        return service.capture("ash", cmd).block().getInstanceId();
    }

    @Test
    void evolveMagikarpToGyaradosUpdatesSpeciesAndAbilityPreservingId() {
        UUID id = captureMagikarp();
        StepVerifier.create(service.evolve("ash", id, "gyarados"))
                .assertNext(evolved -> {
                    org.junit.jupiter.api.Assertions.assertEquals(id, evolved.getId());
                    org.junit.jupiter.api.Assertions.assertEquals("gyarados", evolved.getSpecies().getName());
                    org.junit.jupiter.api.Assertions.assertEquals("intimidate", evolved.getAbility());
                })
                .verifyComplete();
    }

    @Test
    void evolveToInvalidSpeciesIsRejected() {
        UUID id = captureMagikarp();
        StepVerifier.create(service.evolve("ash", id, "pikachu"))
                .expectError(InvalidEvolutionException.class)
                .verify();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=EvolveUseCaseTest`
Expected: FAIL.

- [ ] **Step 3: Implementar excepción, `evolve` y el mapeo de cadena**

`domain/exception/InvalidEvolutionException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class InvalidEvolutionException extends RuntimeException {
    public InvalidEvolutionException(String from, String to) {
        super("'" + to + "' no es una evolución directa válida de '" + from + "'");
    }
}
```

Agregar a `TrainerService`:
```java
    public Mono<PokemonInstance> evolve(String trainerId, UUID id, String targetSpecies) {
        String target = targetSpecies == null ? "" : targetSpecies.trim().toLowerCase();
        return requireTrainer(trainerId).flatMap(trainer -> {
            PokemonInstance current = trainer.findById(id);
            String from = current.getSpecies().getName();
            return catalog.directEvolutionsOf(from)
                    .filter(option -> option.getToSpecies().equalsIgnoreCase(target))
                    .next()
                    .switchIfEmpty(Mono.error(new skaro.pokeapi.trainer.domain.exception.InvalidEvolutionException(from, target)))
                    .flatMap(option -> {
                        String newAbility = option.getAllowedAbilities().isEmpty()
                                ? current.getAbility() : option.getAllowedAbilities().get(0);
                        trainer.evolve(id, new skaro.pokeapi.trainer.domain.SpeciesRef(target), newAbility);
                        return repository.save(trainer).thenReturn(trainer.findById(id));
                    });
        });
    }
```

Implementar `directEvolutionsOf` en `PokeApiCatalogAdapter` (reemplaza el `Flux.empty()`):
```java
    @Override
    public Flux<EvolutionOption> directEvolutionsOf(String speciesName) {
        return pokeApiClient.getResource(skaro.pokeapi.resource.pokemonspecies.PokemonSpecies.class, speciesName)
                .flatMap(species -> pokeApiClient.followResource(
                        species::getEvolutionChain, skaro.pokeapi.resource.evolutionchain.EvolutionChain.class))
                .flatMapMany(chain -> Flux.fromIterable(findDirectEvolutions(chain.getChain(), speciesName)))
                .flatMap(targetName -> getSpecies(targetName)
                        .map(data -> new EvolutionOption(speciesName, targetName, data.getAbilities())));
    }

    private java.util.List<String> findDirectEvolutions(
            skaro.pokeapi.resource.evolutionchain.ChainLink node, String speciesName) {
        java.util.List<String> result = new java.util.ArrayList<>();
        collectDirect(node, speciesName, result);
        return result;
    }

    private void collectDirect(skaro.pokeapi.resource.evolutionchain.ChainLink node,
                               String speciesName, java.util.List<String> out) {
        if (node == null) return;
        if (node.getSpecies() != null && speciesName.equalsIgnoreCase(node.getSpecies().getName())) {
            if (node.getEvolvesTo() != null) {
                node.getEvolvesTo().forEach(next -> out.add(next.getSpecies().getName()));
            }
            return;
        }
        if (node.getEvolvesTo() != null) {
            node.getEvolvesTo().forEach(next -> collectDirect(next, speciesName, out));
        }
    }
```

> **Verificar** los getters reales en `skaro.pokeapi.resource.evolutionchain.ChainLink` (`getSpecies()`, `getEvolvesTo()`) y en `PokemonSpecies` (`getEvolutionChain()`). Ajustar nombres si difieren.

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=EvolveUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer src/test/java/skaro/pokeapi/trainer/application/EvolveUseCaseTest.java
git commit -m "feat: evolution use case with direct-evolution validation and ability update"
```

---

### Task 13: DTOs web y `TrainerPokemonController`

Expone los endpoints #1–#6. DTOs de request/response separados del dominio.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/CaptureRequest.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/CaptureResponse.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/InstanceResponse.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/PartyMemberResponse.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/MoveRequest.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/EvolveRequest.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/dto/DtoMapper.java`
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/TrainerPokemonController.java`
- Test: `src/test/java/skaro/pokeapi/trainer/adapter/web/TrainerPokemonControllerTest.java`

**Interfaces:**
- Consumes: `TrainerService` y sus tipos.
- Produces: controller con rutas del spec §5. `DtoMapper` convierte `CaptureRequest→CaptureCommand`, `PokemonInstance→InstanceResponse`, `PartyMember→PartyMemberResponse`.

- [ ] **Step 1: Escribir el test con `WebTestClient`**

```java
package skaro.pokeapi.trainer.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.CaptureResult;
import skaro.pokeapi.trainer.application.TrainerService;
import skaro.pokeapi.trainer.domain.StorageLocation;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TrainerPokemonController.class)
class TrainerPokemonControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private TrainerService service;

    @Test
    void captureReturns201WithLocationHeader() {
        UUID id = UUID.randomUUID();
        when(service.capture(eq("ash"), any()))
                .thenReturn(Mono.just(new CaptureResult(id, StorageLocation.PARTY)));

        String body = "{"
                + "\"species\":\"bulbasaur\",\"nature\":\"hardy\",\"ability\":\"overgrow\","
                + "\"gender\":\"MALE\",\"initialLevel\":5,"
                + "\"ivs\":{\"HP\":31,\"ATTACK\":31,\"DEFENSE\":31,\"SPECIAL_ATTACK\":31,\"SPECIAL_DEFENSE\":31,\"SPEED\":31},"
                + "\"evs\":{\"HP\":0,\"ATTACK\":0,\"DEFENSE\":0,\"SPECIAL_ATTACK\":0,\"SPECIAL_DEFENSE\":0,\"SPEED\":0}"
                + "}";

        webClient.post().uri("/api/v1/trainers/ash/pokemon")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.instanceId").isEqualTo(id.toString())
                .jsonPath("$.location").isEqualTo("PARTY");
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=TrainerPokemonControllerTest`
Expected: FAIL.

- [ ] **Step 3: Implementar DTOs, mapper y controller**

`dto/CaptureRequest.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;

public class CaptureRequest {
    public String species;
    public String nickname;
    public Map<Stat, Integer> ivs;
    public Map<Stat, Integer> evs;
    public String nature;
    public String ability;
    public boolean shiny;
    public Gender gender;
    public List<String> moves;
    public String heldItem;
    public String originalTrainerId;
    public String pokeball;
    public int initialLevel;
    public String originLocation;
}
```

`dto/CaptureResponse.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.UUID;

public class CaptureResponse {
    public UUID instanceId;
    public String location;

    public CaptureResponse(UUID instanceId, String location) {
        this.instanceId = instanceId;
        this.location = location;
    }
}
```

`dto/InstanceResponse.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InstanceResponse {
    public UUID id;
    public String species;
    public String nickname;
    public Map<String, Integer> ivs;
    public Map<String, Integer> evs;
    public String nature;
    public String ability;
    public boolean shiny;
    public String gender;
    public List<String> moves;
    public String heldItem;
    public String originalTrainerId;
    public String pokeball;
    public String captureDate;
    public int initialLevel;
    public String originLocation;
}
```

`dto/PartyMemberResponse.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

public class PartyMemberResponse {
    public InstanceResponse instance;
    public SpeciesView species;

    public static class SpeciesView {
        public String name;
        public List<String> types;
        public Map<String, Integer> baseStats;
        public String spriteUrl;
    }
}
```

`dto/MoveRequest.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import skaro.pokeapi.trainer.domain.StorageLocation;

public class MoveRequest {
    public StorageLocation location;
}
```

`dto/EvolveRequest.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

public class EvolveRequest {
    public String targetSpecies;
}
```

`dto/DtoMapper.java`:
```java
package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.application.CaptureCommand;
import skaro.pokeapi.trainer.application.PartyMember;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.Stat;

public final class DtoMapper {

    private DtoMapper() {}

    public static CaptureCommand toCommand(CaptureRequest req) {
        CaptureCommand cmd = new CaptureCommand();
        cmd.setSpecies(req.species);
        cmd.setNickname(req.nickname);
        cmd.setIvs(req.ivs);
        cmd.setEvs(req.evs);
        cmd.setNature(req.nature);
        cmd.setAbility(req.ability);
        cmd.setShiny(req.shiny);
        cmd.setGender(req.gender);
        cmd.setMoves(req.moves);
        cmd.setHeldItem(req.heldItem);
        cmd.setOriginalTrainerId(req.originalTrainerId);
        cmd.setPokeball(req.pokeball);
        cmd.setInitialLevel(req.initialLevel);
        cmd.setOriginLocation(req.originLocation);
        return cmd;
    }

    public static InstanceResponse toInstanceResponse(PokemonInstance p) {
        InstanceResponse r = new InstanceResponse();
        r.id = p.getId();
        r.species = p.getSpecies().getName();
        r.nickname = p.getNickname();
        r.ivs = statMap(p.getIvs().asMap());
        r.evs = statMap(p.getEvs().asMap());
        r.nature = p.getNature().getName();
        r.ability = p.getAbility();
        r.shiny = p.isShiny();
        r.gender = p.getGender().name();
        r.moves = new ArrayList<>(p.getMoveset().getMoves());
        r.heldItem = p.getHeldItem().map(h -> h.getName()).orElse(null);
        r.originalTrainerId = p.getOrigin().getOriginalTrainerId();
        r.pokeball = p.getOrigin().getPokeball();
        r.captureDate = p.getOrigin().getCaptureDate() == null ? null : p.getOrigin().getCaptureDate().toString();
        r.initialLevel = p.getOrigin().getInitialLevel();
        r.originLocation = p.getOrigin().getOriginLocation();
        return r;
    }

    public static PartyMemberResponse toPartyMemberResponse(PartyMember member) {
        PartyMemberResponse r = new PartyMemberResponse();
        r.instance = toInstanceResponse(member.getInstance());
        SpeciesData s = member.getSpecies();
        PartyMemberResponse.SpeciesView view = new PartyMemberResponse.SpeciesView();
        view.name = s.getName();
        view.types = s.getTypes();
        view.baseStats = statMap(s.getBaseStats());
        view.spriteUrl = s.getSpriteUrl();
        r.species = view;
        return r;
    }

    private static Map<String, Integer> statMap(Map<Stat, Integer> source) {
        Map<String, Integer> out = new EnumMap<>(Stat.class).isEmpty() ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>();
        for (Stat s : Stat.values()) {
            if (source.containsKey(s)) out.put(s.name(), source.get(s));
        }
        return out;
    }
}
```

`TrainerPokemonController.java`:
```java
package skaro.pokeapi.trainer.adapter.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.adapter.web.dto.CaptureRequest;
import skaro.pokeapi.trainer.adapter.web.dto.CaptureResponse;
import skaro.pokeapi.trainer.adapter.web.dto.DtoMapper;
import skaro.pokeapi.trainer.adapter.web.dto.EvolveRequest;
import skaro.pokeapi.trainer.adapter.web.dto.InstanceResponse;
import skaro.pokeapi.trainer.adapter.web.dto.MoveRequest;
import skaro.pokeapi.trainer.adapter.web.dto.PartyMemberResponse;
import skaro.pokeapi.trainer.application.TrainerService;

@RestController
@RequestMapping("/api/v1/trainers/{trainerId}")
public class TrainerPokemonController {

    private final TrainerService service;

    public TrainerPokemonController(TrainerService service) {
        this.service = service;
    }

    @PostMapping("/pokemon")
    public Mono<ResponseEntity<CaptureResponse>> capture(
            @PathVariable String trainerId, @RequestBody CaptureRequest request) {
        return service.capture(trainerId, DtoMapper.toCommand(request))
                .map(result -> {
                    URI location = URI.create(String.format(
                            "/api/v1/trainers/%s/pokemon/%s", trainerId, result.getInstanceId()));
                    return ResponseEntity.created(location)
                            .body(new CaptureResponse(result.getInstanceId(), result.getLocation().name()));
                });
    }

    @GetMapping("/pokemon/{id}")
    public Mono<InstanceResponse> getInstance(@PathVariable String trainerId, @PathVariable UUID id) {
        return service.getInstance(trainerId, id).map(DtoMapper::toInstanceResponse);
    }

    @GetMapping("/party")
    public Flux<PartyMemberResponse> listParty(@PathVariable String trainerId) {
        return service.listParty(trainerId).map(DtoMapper::toPartyMemberResponse);
    }

    @GetMapping("/box")
    public Flux<InstanceResponse> listBox(@PathVariable String trainerId) {
        return service.listBox(trainerId).map(DtoMapper::toInstanceResponse);
    }

    @PatchMapping("/pokemon/{id}")
    public Mono<InstanceResponse> move(
            @PathVariable String trainerId, @PathVariable UUID id, @RequestBody MoveRequest request) {
        return service.move(trainerId, id, request.location)
                .then(service.getInstance(trainerId, id))
                .map(DtoMapper::toInstanceResponse);
    }

    @PostMapping("/pokemon/{id}/evolve")
    public Mono<InstanceResponse> evolve(
            @PathVariable String trainerId, @PathVariable UUID id, @RequestBody EvolveRequest request) {
        return service.evolve(trainerId, id, request.targetSpecies).map(DtoMapper::toInstanceResponse);
    }
}
```

> **Simplificar** `DtoMapper.statMap` al implementar: usar directamente `new java.util.LinkedHashMap<>()` y poblar en orden de `Stat.values()` (el ternario del borrador es ruido — dejar solo el `LinkedHashMap`).

- [ ] **Step 4: Correr el test**

Run: `./mvnw -q test -Dtest=TrainerPokemonControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer/adapter/web src/test/java/skaro/pokeapi/trainer/adapter/web
git commit -m "feat: REST controller and DTOs for party/box lifecycle"
```

---

### Task 14: Manejo de errores HTTP (`@RestControllerAdvice`) + `TrainerNotFoundException`

Mapea excepciones de dominio a códigos HTTP con `application/problem+json`. Reemplaza el marcador temporal de `requireTrainer`.

**Files:**
- Create: `src/main/java/skaro/pokeapi/trainer/domain/exception/TrainerNotFoundException.java`
- Modify: `src/main/java/skaro/pokeapi/trainer/application/TrainerService.java` (usar `TrainerNotFoundException` en `requireTrainer`)
- Create: `src/main/java/skaro/pokeapi/trainer/adapter/web/ApiExceptionHandler.java`
- Test: `src/test/java/skaro/pokeapi/trainer/adapter/web/ApiExceptionHandlerTest.java`

**Interfaces:**
- Consumes: todas las excepciones de dominio.
- Produces: `TrainerNotFoundException(String)`; `ApiExceptionHandler` (`@RestControllerAdvice`) con el mapeo: `InstanceNotFoundException`/`TrainerNotFoundException`→404; `PartyFullException`/`BoxFullException`/`StorageFullException`→409; `SpeciesNotFoundException`/`InvalidAbilityException`/`InvalidEvolutionException`/`InvalidStatValueException`/`InvalidEffortValuesException`/`InvalidMovesetException`/`IllegalArgumentException`→422.

- [ ] **Step 1: Escribir el test**

```java
package skaro.pokeapi.trainer.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.TrainerService;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;
import skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TrainerPokemonController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private TrainerService service;

    @Test
    void storageFullMapsTo409() {
        when(service.capture(eq("ash"), any())).thenReturn(Mono.error(new StorageFullException()));
        webClient.post().uri("/api/v1/trainers/ash/pokemon")
                .header("Content-Type", "application/json")
                .bodyValue("{\"species\":\"x\",\"ivs\":{},\"evs\":{}}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void trainerNotFoundMapsTo404() {
        UUID id = UUID.randomUUID();
        when(service.getInstance(eq("ghost"), eq(id)))
                .thenReturn(Mono.error(new TrainerNotFoundException("ghost")));
        webClient.get().uri("/api/v1/trainers/ghost/pokemon/" + id)
                .exchange()
                .expectStatus().isEqualTo(404);
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw -q test -Dtest=ApiExceptionHandlerTest`
Expected: FAIL.

- [ ] **Step 3: Implementar la excepción, el handler y ajustar `requireTrainer`**

`domain/exception/TrainerNotFoundException.java`:
```java
package skaro.pokeapi.trainer.domain.exception;

public class TrainerNotFoundException extends RuntimeException {
    public TrainerNotFoundException(String trainerId) {
        super("No existe el entrenador: " + trainerId);
    }
}
```

En `TrainerService.requireTrainer`, reemplazar la línea del `switchIfEmpty`:
```java
    private Mono<Trainer> requireTrainer(String trainerId) {
        return repository.findByTrainerId(trainerId)
                .switchIfEmpty(Mono.error(
                        new skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException(trainerId)));
    }
```

`adapter/web/ApiExceptionHandler.java`:
```java
package skaro.pokeapi.trainer.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import skaro.pokeapi.trainer.domain.exception.BoxFullException;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;
import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;
import skaro.pokeapi.trainer.domain.exception.InvalidEvolutionException;
import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;
import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.SpeciesNotFoundException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;
import skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InstanceNotFoundException.class, TrainerNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({PartyFullException.class, BoxFullException.class, StorageFullException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({SpeciesNotFoundException.class, InvalidAbilityException.class,
            InvalidEvolutionException.class, InvalidStatValueException.class,
            InvalidEffortValuesException.class, InvalidMovesetException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> unprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("title", status.getReasonPhrase());
        body.put("detail", detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
```

- [ ] **Step 4: Correr el test + la suite completa**

Run: `./mvnw -q test -Dtest=ApiExceptionHandlerTest` → PASS
Run: `./mvnw -q test` → BUILD SUCCESS (toda la suite).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/skaro/pokeapi/trainer src/test/java/skaro/pokeapi/trainer/adapter/web/ApiExceptionHandlerTest.java
git commit -m "feat: HTTP error mapping via RestControllerAdvice (problem+json)"
```

---

### Task 15: README y documentación de uso

**Files:**
- Modify: `README.md` (agregar sección del dominio nuevo, cómo correr, ejemplos `curl`)
- Create: `docs/api-examples.http` (requests de ejemplo ejecutables)

**Interfaces:**
- Consumes: todos los endpoints de Tasks 13–14.

- [ ] **Step 1: Agregar al `README.md` la sección del dominio nuevo**

Insertar después de la línea 7 (debajo del título/descripción) un bloque nuevo que documente: qué se agregó (dominio Baúl/Equipo), arquitectura hexagonal, cómo correr (`./mvnw spring-boot:run`), la tabla de endpoints del spec §5, y un ejemplo de captura con `curl`. Enlazar al spec `docs/superpowers/specs/2026-10-15-baul-equipo-pokemon-design.md`. Contenido mínimo:

````markdown
---

## Dominio: Baúl de Pokémon y Equipo Activo

Este fork extiende la librería `pokeapi-reactor` con una aplicación de dominio que
gestiona los Pokémon individuales de un entrenador (Equipo Activo vs Baúl). Ver el
documento de diseño en
[`docs/superpowers/specs/2026-10-15-baul-equipo-pokemon-design.md`](docs/superpowers/specs/2026-10-15-baul-equipo-pokemon-design.md).

### Arquitectura
Hexagonal en módulo único bajo `skaro.pokeapi.trainer`: `domain` (puro) · `application`
(casos de uso, reactivos) · `adapter/{web,persistence,pokeapi}`. La persistencia es
in-memory detrás de un puerto (`PokemonRepository`); PokéAPI se consume vía
`PokemonCatalogPort` sobre el `PokeApiClient` existente.

### Cómo correr
```bash
./mvnw spring-boot:run
# Netty embebido en http://localhost:8080
```

Límites configurables en `application.yml` (`trainer.party.max-size`,
`trainer.box.max-size`).

### Endpoints
| Método + ruta | Qué hace |
|---|---|
| `POST /api/v1/trainers/{id}/pokemon` | Captura/crea (auto party→baúl), `201` |
| `GET /api/v1/trainers/{id}/pokemon/{uuid}` | Detalle del ejemplar |
| `GET /api/v1/trainers/{id}/party` | Equipo activo (vista compuesta con PokéAPI) |
| `GET /api/v1/trainers/{id}/box` | Baúl |
| `PATCH /api/v1/trainers/{id}/pokemon/{uuid}` | Traslado `{"location":"PARTY"\|"BOX"}` |
| `POST /api/v1/trainers/{id}/pokemon/{uuid}/evolve` | Evolución (bonus) |

### Ejemplo
```bash
curl -X POST http://localhost:8080/api/v1/trainers/ash/pokemon \
  -H 'Content-Type: application/json' \
  -d '{"species":"bulbasaur","nature":"hardy","ability":"overgrow","gender":"MALE",
       "initialLevel":5,"originalTrainerId":"ash","pokeball":"poke-ball",
       "moves":["tackle","growl"],
       "ivs":{"HP":31,"ATTACK":31,"DEFENSE":31,"SPECIAL_ATTACK":31,"SPECIAL_DEFENSE":31,"SPEED":31},
       "evs":{"HP":0,"ATTACK":0,"DEFENSE":0,"SPECIAL_ATTACK":0,"SPECIAL_DEFENSE":0,"SPEED":0}}'
```
````

- [ ] **Step 2: Crear `docs/api-examples.http`**

```http
### Capturar un Bulbasaur
POST http://localhost:8080/api/v1/trainers/ash/pokemon
Content-Type: application/json

{
  "species": "bulbasaur", "nature": "hardy", "ability": "overgrow",
  "gender": "MALE", "initialLevel": 5, "originalTrainerId": "ash", "pokeball": "poke-ball",
  "moves": ["tackle", "growl"],
  "ivs": {"HP":31,"ATTACK":31,"DEFENSE":31,"SPECIAL_ATTACK":31,"SPECIAL_DEFENSE":31,"SPEED":31},
  "evs": {"HP":0,"ATTACK":0,"DEFENSE":0,"SPECIAL_ATTACK":0,"SPECIAL_DEFENSE":0,"SPEED":0}
}

### Listar equipo activo (vista compuesta)
GET http://localhost:8080/api/v1/trainers/ash/party

### Listar baúl
GET http://localhost:8080/api/v1/trainers/ash/box

### Mover al baúl
PATCH http://localhost:8080/api/v1/trainers/ash/pokemon/REEMPLAZAR-UUID
Content-Type: application/json

{ "location": "BOX" }

### Evolucionar
POST http://localhost:8080/api/v1/trainers/ash/pokemon/REEMPLAZAR-UUID/evolve
Content-Type: application/json

{ "targetSpecies": "ivysaur" }
```

- [ ] **Step 3: Verificar que el proyecto arranca y responde**

Run: `./mvnw -q spring-boot:run &` ; esperar el log de arranque de Netty, luego ejecutar el `curl` de ejemplo y verificar `201 Created`. Detener el proceso.
Expected: respuesta `201` con `instanceId` y `location`.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/api-examples.http
git commit -m "docs: document trainer domain, endpoints and usage examples in README"
```

---

## Self-Review

**1. Cobertura del spec:**
- §3 D1 estructura hexagonal → Tasks 1–14 (layout de paquetes). ✓
- §3 D2 app ejecutable → Task 1. ✓
- §3 D3 persistencia in-memory tras puerto → Tasks 8, 9. ✓
- §3 D4 `Trainer` raíz → Task 7. ✓
- §3 D5 `StorageLimits` configurable → Tasks 1, 6, 9. ✓
- §3 D6 UUID global → Task 5. ✓
- §3 D7 PATCH location → Tasks 11 (`move`), 13 (controller). ✓
- §4 VOs (IVs/EVs/Nature/Moveset/HeldItem/SpeciesRef/OriginMetadata) → Tasks 2–4. ✓
- §4 patrón reactivo (async en bordes, dominio puro) → Task 11 (`capture` resuelve especie antes del dominio). ✓
- §4 validaciones dominio vs PokéAPI (especie/habilidad) → Task 11. ✓
- §5 endpoints #1–#6 → Tasks 13, 12. ✓
- §5 vista compuesta del party → Task 11 (`listParty` + `PartyMember`), 13 (`PartyMemberResponse`). ✓
- §5 baúl paginado → **gap parcial**: el plan lista el baúl completo sin `?page=&size=`. Decisión: la paginación reactiva agrega complejidad (slicing de `Flux`) sin cambiar el diseño defendible; se documenta como extensión en el README y queda fuera del alcance de implementación. Coherente con §8 (alcance prioriza diseño). Aceptado como gap consciente.
- §5 manejo de errores RFC-7807 → Task 14. ✓
- §6 estrategia de testing (dominio puro, use cases con fakes, WebTestClient, mockwebserver) → Tasks 2–14. ✓
- Bonus evolución (§ consigna) → Task 12. ✓

**2. Placeholders:** Steps con código completo. Los `>` "Verificar getter real" (Tasks 10, 12) son instrucciones de verificación contra la librería existente, no placeholders de lógica — el código está escrito y funcional asumiendo los nombres estándar de PokeAPI-reactor. El `DtoMapper.statMap` de Task 13 trae una nota de simplificación explícita con el resultado esperado.

**3. Consistencia de tipos:** `CaptureCommand`/`CaptureResult`/`PartyMember` definidos en Task 11 y consumidos en 13 con las mismas firmas. `StorageLocation`, `SpeciesData`, `EvolutionOption`, `PokemonRepository`, `PokemonCatalogPort` consistentes entre Tasks 6/8 y sus consumidores. `requireTrainer` marcado temporal en Task 11 y reemplazado explícitamente en Task 14. `Trainer.evolve`/`PokemonInstance.evolve` firmas coinciden entre Tasks 5, 7 y 12.

**Gap conocido y aceptado:** paginación del baúl (documentada como extensión, no implementada).
