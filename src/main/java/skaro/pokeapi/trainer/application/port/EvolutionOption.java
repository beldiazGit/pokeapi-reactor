package skaro.pokeapi.trainer.application.port;

import java.util.List;

public class EvolutionOption {
    private final String fromSpecies;
    private final String toSpecies;
    private final List<String> allowedAbilities;

    public EvolutionOption(String fromSpecies, String toSpecies, List<String> allowedAbilities) {
        this.fromSpecies = fromSpecies;
        this.toSpecies = toSpecies;
        this.allowedAbilities = allowedAbilities;
    }

    public String getFromSpecies() { return fromSpecies; }
    public String getToSpecies() { return toSpecies; }
    public List<String> getAllowedAbilities() { return allowedAbilities; }
}
