package skaro.pokeapi.trainer.adapter.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.port.OptimisticLockException;
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
        // Copy-on-load: hand back an isolated copy, never the stored reference,
        // so callers mutate their own aggregate and cannot corrupt the store.
        return Mono.defer(() -> Mono.justOrEmpty(store.get(trainerId)).map(Trainer::new));
    }

    @Override
    public Mono<Trainer> loadOrCreate(String trainerId) {
        // Atomically create-if-absent at version 0, then return an isolated copy.
        return Mono.fromSupplier(() -> new Trainer(store.computeIfAbsent(trainerId,
                id -> new Trainer(id, new StorageLimits(
                        properties.getParty().getMaxSize(),
                        properties.getBox().getMaxSize())))));
    }

    @Override
    public Mono<Trainer> save(Trainer trainer) {
        // Compare-and-swap, made atomic per key by ConcurrentHashMap.compute: the
        // remapping function runs under the bin lock for that key, so the
        // version check and the replacement are a single atomic step.
        return Mono.fromSupplier(() -> {
            Trainer stored = store.compute(trainer.getTrainerId(), (k, existing) -> {
                if (existing != null && existing.getVersion() != trainer.getVersion()) {
                    throw new OptimisticLockException(k);
                }
                return trainer.nextVersion();
            });
            return new Trainer(stored); // hand back an isolated copy at the new version
        });
    }
}
