package skaro.pokeapi.trainer.application.port;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Stat;

public class SpeciesData {
    private final String name;
    private final List<String> abilities;
    private final List<String> types;
    private final Map<Stat, Integer> baseStats;
    private final String spriteUrl;

    public SpeciesData(String name, List<String> abilities, List<String> types,
                       Map<Stat, Integer> baseStats, String spriteUrl) {
        this.name = name;
        this.abilities = abilities;
        this.types = types;
        this.baseStats = baseStats;
        this.spriteUrl = spriteUrl;
    }

    public String getName() { return name; }
    public List<String> getAbilities() { return abilities; }
    public List<String> getTypes() { return types; }
    public Map<Stat, Integer> getBaseStats() { return baseStats; }
    public String getSpriteUrl() { return spriteUrl; }
}
