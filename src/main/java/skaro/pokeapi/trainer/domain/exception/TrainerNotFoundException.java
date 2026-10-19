package skaro.pokeapi.trainer.domain.exception;

public class TrainerNotFoundException extends RuntimeException {
    public TrainerNotFoundException(String trainerId) {
        super("Trainer not found: " + trainerId);
    }
}
