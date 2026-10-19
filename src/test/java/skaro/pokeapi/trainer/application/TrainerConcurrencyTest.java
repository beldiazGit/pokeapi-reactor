package skaro.pokeapi.trainer.application;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.adapter.persistence.InMemoryPokemonRepository;
import skaro.pokeapi.trainer.application.port.EvolutionOption;
import skaro.pokeapi.trainer.application.port.OptimisticLockException;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.PokemonRepository;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.config.StorageProperties;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;
import skaro.pokeapi.trainer.domain.StorageLimits;
import skaro.pokeapi.trainer.domain.StorageLocation;
import skaro.pokeapi.trainer.domain.Trainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerConcurrencyTest {

    private static final String SPECIES = "bulbasaur";

    private PokemonCatalogPort fakeCatalog() {
        return new PokemonCatalogPort() {
            @Override public Mono<SpeciesData> getSpecies(String name) {
                if (!SPECIES.equals(name)) return Mono.empty();
                Map<Stat, Integer> base = new EnumMap<>(Stat.class);
                for (Stat s : Stat.values()) base.put(s, 45);
                return Mono.just(new SpeciesData(SPECIES,
                        Arrays.asList("overgrow", "chlorophyll"),
                        Arrays.asList("grass", "poison"), base, "http://img/1.png"));
            }
            @Override public Flux<EvolutionOption> directEvolutionsOf(String name) {
                return Flux.empty();
            }
        };
    }

    private CaptureCommand command() {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        CaptureCommand cmd = new CaptureCommand();
        cmd.setSpecies(SPECIES);
        cmd.setNickname("Bulby");
        cmd.setIvs(ivs);
        cmd.setEvs(evs);
        cmd.setNature("hardy");
        cmd.setAbility("overgrow");
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

    private StorageProperties props(int party, int box) {
        StorageProperties p = new StorageProperties();
        p.getParty().setMaxSize(party);
        p.getBox().setMaxSize(box);
        return p;
    }

    /**
     * Test 3 - deterministic retry: a repository double whose save() conflicts the
     * FIRST time and succeeds the second. capture() must ultimately succeed via the
     * retry, and save() must be invoked exactly twice.
     */
    @Test
    void captureRetriesAndSucceedsAfterOneOptimisticConflict() {
        StorageLimits limits = new StorageLimits(6, 30);
        AtomicInteger saveCalls = new AtomicInteger(0);

        PokemonRepository flakyRepo = new PokemonRepository() {
            @Override public Mono<Trainer> findByTrainerId(String id) {
                return Mono.just(new Trainer(id, limits));
            }
            @Override public Mono<Trainer> loadOrCreate(String id) {
                // Always a fresh copy, like copy-on-load after a conflicting write.
                return Mono.just(new Trainer(id, limits));
            }
            @Override public Mono<Trainer> save(Trainer trainer) {
                int call = saveCalls.incrementAndGet();
                if (call == 1) {
                    return Mono.error(new OptimisticLockException(trainer.getTrainerId()));
                }
                return Mono.just(trainer.nextVersion());
            }
        };

        TrainerService service = new TrainerService(flakyRepo, fakeCatalog());

        CaptureResult result = service.capture("ash", command()).block();

        assertNotNull(result);
        assertEquals(StorageLocation.PARTY, result.getLocation());
        assertNotNull(result.getInstanceId());
        assertEquals(2, saveCalls.get(), "save should be retried exactly once (2 calls total)");
    }

    /**
     * Test 4 - concurrency stress (the headline test). N real threads fire capture()
     * for the SAME trainer, released together by a CountDownLatch, against a real
     * InMemoryPokemonRepository + real TrainerService. Asserts no lost updates and
     * that the party cap was never breached.
     */
    @Test
    void concurrentCapturesNeverLoseUpdatesNorExceedPartyCap() throws Exception {
        int n = 20;
        int partyMax = 6;
        int boxMax = 30; // party 6 + box 30 = 36 >= 20, so all N fit
        InMemoryPokemonRepository repo = new InMemoryPokemonRepository(props(partyMax, boxMax));
        TrainerService service = new TrainerService(repo, fakeCatalog());

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    service.capture("ash", command()).block(); // blocking allowed in tests
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown(); // release all workers together
        assertTrue(done.await(30, TimeUnit.SECONDS), "captures did not finish in time");
        pool.shutdownNow();

        assertTrue(failures.isEmpty(), "no capture should fail: " + describe(failures));

        Trainer stored = repo.findByTrainerId("ash").block();
        assertNotNull(stored);
        int partySize = stored.party().size();
        int boxSize = stored.box().size();

        assertEquals(n, partySize + boxSize, "no lost updates: all N instances must be stored");
        assertEquals(Math.min(n, partyMax), partySize, "party should be filled to its cap first");
        assertTrue(partySize <= partyMax, "party cap must never be exceeded");
    }

    private String describe(ConcurrentLinkedQueue<Throwable> failures) {
        List<String> msgs = new ArrayList<>();
        for (Throwable t : failures) msgs.add(t.toString());
        return msgs.toString();
    }
}
