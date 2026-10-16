package skaro.pokeapi.trainer.domain.exception;

public class BoxFullException extends RuntimeException {
    public BoxFullException(int max) {
        super("Box is full (max " + max + ")");
    }
}
