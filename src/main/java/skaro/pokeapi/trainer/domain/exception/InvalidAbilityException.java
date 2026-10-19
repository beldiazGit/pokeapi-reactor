package skaro.pokeapi.trainer.domain.exception;

public class InvalidAbilityException extends RuntimeException {
    public InvalidAbilityException(String ability, String species) {
        super("Ability '" + ability + "' does not belong to species '" + species + "'");
    }
}
