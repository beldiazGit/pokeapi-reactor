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
