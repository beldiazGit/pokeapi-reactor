package skaro.pokeapi.trainer.domain.exception;

public class InvalidEvolutionException extends RuntimeException {
    public InvalidEvolutionException(String from, String to) {
        super("'" + to + "' is not a valid direct evolution of '" + from + "'");
    }
}
