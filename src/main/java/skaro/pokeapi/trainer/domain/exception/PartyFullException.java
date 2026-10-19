package skaro.pokeapi.trainer.domain.exception;

public class PartyFullException extends RuntimeException {
    public PartyFullException(int max) {
        super("Active party is full (max " + max + ")");
    }
}
