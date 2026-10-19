package skaro.pokeapi.trainer.application;

import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.PokemonInstance;

public class PartyMember {
    private final PokemonInstance instance;
    private final SpeciesData species;

    public PartyMember(PokemonInstance instance, SpeciesData species) {
        this.instance = instance;
        this.species = species;
    }

    public PokemonInstance getInstance() { return instance; }
    public SpeciesData getSpecies() { return species; }
}
