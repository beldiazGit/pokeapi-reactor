package skaro.pokeapi.trainer.adapter.pokeapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.PokeApiReactorBaseConfiguration;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.resource.evolutionchain.ChainLink;
import skaro.pokeapi.resource.evolutionchain.EvolutionChain;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.resource.pokemon.PokemonStat;
import skaro.pokeapi.resource.pokemonspecies.PokemonSpecies;
import skaro.pokeapi.trainer.application.port.EvolutionOption;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.Stat;

@Component
public class PokeApiCatalogAdapter implements PokemonCatalogPort {

    private final PokeApiClient pokeApiClient;
    private final WebClient webClient;

    public PokeApiCatalogAdapter(PokeApiClient pokeApiClient,
            @Qualifier(PokeApiReactorBaseConfiguration.POKEAPI_WEBCLIENT_BEAN) WebClient webClient) {
        this.pokeApiClient = pokeApiClient;
        this.webClient = webClient;
    }

    @Override
    public Mono<SpeciesData> getSpecies(String speciesName) {
        return pokeApiClient.getResource(Pokemon.class, speciesName)
                .map(this::toSpeciesData);
    }

    @Override
    public Flux<EvolutionOption> directEvolutionsOf(String speciesName) {
        return pokeApiClient.getResource(PokemonSpecies.class, speciesName)
                .flatMap(this::fetchEvolutionChain)
                .flatMapMany(chain -> Flux.fromIterable(findDirectEvolutions(chain.getChain(), speciesName)))
                .flatMap(targetName -> getSpecies(targetName)
                        .map(data -> new EvolutionOption(speciesName, targetName, data.getAbilities())));
    }

    // PokemonSpecies.getEvolutionChain() returns an ApiResource<EvolutionChain> (unnamed
    // resource, identified only by URL). EvolutionChain does not implement PokeApiResource
    // (it has no name), so it cannot be fetched via PokeApiClient.getResource/followResource,
    // both of which require a PokeApiResource type bound. Fetch it directly by URL instead,
    // mirroring what WebClientEntityFactory.getNamedResource does internally for named resources.
    //
    // KNOWN LIMITATION: CacheFacade only wraps named-resource lookups made through
    // PokeApiClient.getResource; it has no entry point for this unnamed, URL-only fetch.
    // As a result, following the evolution chain by URL bypasses the cache entirely —
    // every call to evolve()/directEvolutionsOf() re-fetches and re-decodes the chain from
    // the network, even for a species whose chain was just fetched. Accepted for now; would
    // need a URL-keyed cache (or promoting EvolutionChain to a cacheable PokeApiResource) to fix.
    private Mono<EvolutionChain> fetchEvolutionChain(PokemonSpecies species) {
        return webClient.get()
                .uri(URI.create(species.getEvolutionChain().getUrl()))
                .retrieve()
                .bodyToMono(EvolutionChain.class);
    }

    private List<String> findDirectEvolutions(ChainLink node, String speciesName) {
        List<String> result = new ArrayList<>();
        collectDirect(node, speciesName, result);
        return result;
    }

    private void collectDirect(ChainLink node, String speciesName, List<String> out) {
        if (node == null) {
            return;
        }
        if (node.getSpecies() != null && speciesName.equalsIgnoreCase(node.getSpecies().getName())) {
            if (node.getEvolvesTo() != null) {
                node.getEvolvesTo().forEach(next -> out.add(next.getSpecies().getName()));
            }
            return;
        }
        if (node.getEvolvesTo() != null) {
            node.getEvolvesTo().forEach(next -> collectDirect(next, speciesName, out));
        }
    }

    private SpeciesData toSpeciesData(Pokemon pokemon) {
        List<String> abilities = new ArrayList<>();
        pokemon.getAbilities().forEach(a -> abilities.add(a.getAbility().getName()));

        List<String> types = new ArrayList<>();
        pokemon.getTypes().forEach(t -> types.add(t.getType().getName()));

        Map<Stat, Integer> baseStats = new EnumMap<>(Stat.class);
        for (PokemonStat stat : pokemon.getStats()) {
            Stat mapped = mapStat(stat.getStat().getName());
            if (mapped != null) {
                baseStats.put(mapped, stat.getBaseStat());
            }
        }

        String spriteUrl = pokemon.getSprites() == null ? null : pokemon.getSprites().getFrontDefault();
        return new SpeciesData(pokemon.getName(), abilities, types, baseStats, spriteUrl);
    }

    private Stat mapStat(String pokeApiName) {
        switch (pokeApiName) {
            case "hp": return Stat.HP;
            case "attack": return Stat.ATTACK;
            case "defense": return Stat.DEFENSE;
            case "special-attack": return Stat.SPECIAL_ATTACK;
            case "special-defense": return Stat.SPECIAL_DEFENSE;
            case "speed": return Stat.SPEED;
            default: return null;
        }
    }
}
