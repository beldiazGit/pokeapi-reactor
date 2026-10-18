package skaro.pokeapi.trainer.adapter.persistence;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import skaro.pokeapi.trainer.application.port.OptimisticLockException;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.domain.EffortValues;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.IndividualValues;
import skaro.pokeapi.trainer.domain.Moveset;
import skaro.pokeapi.trainer.domain.Nature;
import skaro.pokeapi.trainer.domain.OriginMetadata;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.SpeciesRef;
import skaro.pokeapi.trainer.domain.Stat;
import skaro.pokeapi.trainer.domain.Trainer;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // --- Optimistic locking ---------------------------------------------------

    private PokemonInstance anyInstance() {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef("bulbasaur"))
                .nickname("Bulby")
                .ivs(new IndividualValues(ivs))
                .evs(new EffortValues(evs))
                .nature(new Nature("hardy"))
                .ability("overgrow")
                .shiny(false)
                .gender(Gender.MALE)
                .moveset(new Moveset(Collections.singletonList("tackle")))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "pallet-town"))
                .build();
    }

    @Test
    void saveDetectsStaleVersionWithCompareAndSwap() {
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props());
        repo.loadOrCreate("ash").block(); // stored at version 0

        // Two independent copies, both at version 0.
        Trainer copyA = repo.findByTrainerId("ash").block();
        Trainer copyB = repo.findByTrainerId("ash").block();
        assertEquals(0L, copyA.getVersion());
        assertEquals(0L, copyB.getVersion());

        // Mutate + save A: succeeds, store advances to version 1.
        copyA.capture(anyInstance());
        StepVerifier.create(repo.save(copyA))
                .assertNext(saved -> assertEquals(1L, saved.getVersion()))
                .verifyComplete();

        // Mutate + save B: still carries version 0 -> stale -> conflict.
        copyB.capture(anyInstance());
        StepVerifier.create(repo.save(copyB))
                .expectError(OptimisticLockException.class)
                .verify();
    }

    @Test
    void loadReturnsIsolatedCopyThatDoesNotLeakIntoStore() {
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props());

        // loadOrCreate returns a copy; mutate it WITHOUT saving.
        Trainer loaded = repo.loadOrCreate("ash").block();
        PokemonInstance instance = anyInstance();
        loaded.capture(instance);
        assertTrue(loaded.party().stream().anyMatch(p -> p.getId().equals(instance.getId())));

        // A fresh read must NOT see that unsaved in-memory mutation.
        Trainer reread = repo.findByTrainerId("ash").block();
        assertTrue(reread.party().isEmpty());
        assertFalse(reread.party().stream().anyMatch(p -> p.getId().equals(instance.getId())));
    }
}
