package skaro.pokeapi.trainer.domain.exception;

public class InvalidMovesetException extends RuntimeException {
    public InvalidMovesetException(String message) {
        super(message);
    }
}
