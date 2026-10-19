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
