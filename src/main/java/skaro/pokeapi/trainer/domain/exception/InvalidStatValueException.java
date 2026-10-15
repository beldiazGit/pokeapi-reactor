package skaro.pokeapi.trainer.domain.exception;

public class InvalidStatValueException extends RuntimeException {
    public InvalidStatValueException(String message) {
        super(message);
    }
}
