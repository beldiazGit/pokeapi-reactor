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
