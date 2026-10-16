package skaro.pokeapi.trainer.application.port;

import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.domain.Trainer;

public interface PokemonRepository {
    Mono<Trainer> findByTrainerId(String trainerId);
    Mono<Trainer> loadOrCreate(String trainerId);
    Mono<Trainer> save(Trainer trainer);
}
