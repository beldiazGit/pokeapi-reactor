package skaro.pokeapi.trainer.adapter.pokeapi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.resource.pokemon.PokemonStat;
import skaro.pokeapi.trainer.application.port.EvolutionOption;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.Stat;

@Component
public class PokeApiCatalogAdapter implements PokemonCatalogPort {

    private final PokeApiClient pokeApiClient;

    public PokeApiCatalogAdapter(PokeApiClient pokeApiClient) {
        this.pokeApiClient = pokeApiClient;
    }

    @Override
    public Mono<SpeciesData> getSpecies(String speciesName) {
        return pokeApiClient.getResource(Pokemon.class, speciesName)
                .map(this::toSpeciesData);
    }

    @Override
    public Flux<EvolutionOption> directEvolutionsOf(String speciesName) {
        // Implemented in Task 12 (evolution). For now, contract fulfilled empty.
        return Flux.empty();
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
