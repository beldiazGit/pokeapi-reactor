package skaro.pokeapi.trainer.application.port;

/**
 * Raised by {@link PokemonRepository#save(skaro.pokeapi.trainer.domain.Trainer)}
 * when the aggregate being saved carries a stale version, i.e. another writer has
 * advanced the stored version since this copy was loaded. Part of the repository
 * contract: callers should reload the aggregate and retry.
 */
public class OptimisticLockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OptimisticLockException(String trainerId) {
        super("Concurrent modification of trainer '" + trainerId + "'");
    }
}
