package skaro.pokeapi.trainer.domain.exception;

import java.util.UUID;

public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException(UUID id) {
        super("No Pokemon instance with id " + id);
    }
}
