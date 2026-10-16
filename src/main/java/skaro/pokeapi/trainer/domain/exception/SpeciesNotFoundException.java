package skaro.pokeapi.trainer.domain.exception;

public class SpeciesNotFoundException extends RuntimeException {
    public SpeciesNotFoundException(String species) {
        super("Species does not exist in PokeAPI: " + species);
    }
}
