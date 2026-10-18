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
import skaro.pokeapi.trainer.domain.EffortValues;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.IndividualValues;
import skaro.pokeapi.trainer.domain.Moveset;
import skaro.pokeapi.trainer.domain.Nature;
import skaro.pokeapi.trainer.domain.OriginMetadata;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.SpeciesRef;
import skaro.pokeapi.trainer.domain.Stat;
import skaro.pokeapi.trainer.domain.StorageLocation;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;
import skaro.pokeapi.trainer.domain.exception.SpeciesNotFoundException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

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

    @Test
    void listPartyErrorsWhenSpeciesMissingFromCatalog() {
        // Insert an instance directly into storage (bypassing capture's catalog validation)
        // whose species the fake catalog does not know about, to simulate a species that
        // becomes unavailable in PokeAPI after it was captured.
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        PokemonInstance instance = PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef("missingno"))
                .nickname("Ghost")
                .ivs(new IndividualValues(ivs))
                .evs(new EffortValues(evs))
                .nature(new Nature("hardy"))
                .ability("overgrow")
                .shiny(false)
                .gender(Gender.GENDERLESS)
                .moveset(new Moveset(Collections.singletonList("tackle")))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "pallet-town"))
                .build();

        repository.loadOrCreate("ash")
                .doOnNext(trainer -> trainer.capture(instance))
                .flatMap(repository::save)
                .block();

        StepVerifier.create(service.listParty("ash"))
                .expectError(SpeciesNotFoundException.class)
                .verify();
    }

    @Test
    void captureAcceptsMixedCaseSpeciesName() {
        StepVerifier.create(service.capture("ash", command("Bulbasaur", "overgrow")))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(StorageLocation.PARTY, result.getLocation());
                    org.junit.jupiter.api.Assertions.assertNotNull(result.getInstanceId());
                })
                .verifyComplete();
    }

    @Test
    void moveRejectsNullLocation() {
        CaptureResult captured = service.capture("ash", command("bulbasaur", "overgrow")).block();
        StepVerifier.create(service.move("ash", captured.getInstanceId(), null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
