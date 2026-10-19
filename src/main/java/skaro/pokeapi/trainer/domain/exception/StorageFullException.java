package skaro.pokeapi.trainer.domain.exception;

public class StorageFullException extends RuntimeException {
    public StorageFullException() {
        super("No space available in either the active party or the box");
    }
}
