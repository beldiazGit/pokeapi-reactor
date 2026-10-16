package skaro.pokeapi.trainer.application.port;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PokemonCatalogPort {
    Mono<SpeciesData> getSpecies(String speciesName);
    Flux<EvolutionOption> directEvolutionsOf(String speciesName);
}
