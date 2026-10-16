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
