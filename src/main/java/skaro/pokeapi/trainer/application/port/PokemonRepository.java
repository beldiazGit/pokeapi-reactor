package skaro.pokeapi.trainer.application.port;

import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.domain.Trainer;

public interface PokemonRepository {
    Mono<Trainer> findByTrainerId(String trainerId);
    Mono<Trainer> loadOrCreate(String trainerId);

    /**
     * Persists the aggregate with optimistic locking. May error with
     * {@link OptimisticLockException} when the passed aggregate's version is
     * stale (another writer saved first); callers should reload-and-retry.
     */
    Mono<Trainer> save(Trainer trainer);
}
