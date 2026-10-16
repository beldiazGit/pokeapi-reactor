package skaro.pokeapi.trainer.domain;

import java.time.LocalDate;

public final class OriginMetadata {
    private final String originalTrainerId;
    private final String pokeball;
    private final LocalDate captureDate;
    private final int initialLevel;
    private final String originLocation;

    public OriginMetadata(String originalTrainerId, String pokeball, LocalDate captureDate,
                          int initialLevel, String originLocation) {
        if (initialLevel < 1 || initialLevel > 100) {
            throw new IllegalArgumentException("Initial level must be between 1 and 100: " + initialLevel);
        }
        this.originalTrainerId = originalTrainerId;
        this.pokeball = pokeball;
        this.captureDate = captureDate;
        this.initialLevel = initialLevel;
        this.originLocation = originLocation;
    }

    public String getOriginalTrainerId() { return originalTrainerId; }
    public String getPokeball() { return pokeball; }
    public LocalDate getCaptureDate() { return captureDate; }
    public int getInitialLevel() { return initialLevel; }
    public String getOriginLocation() { return originLocation; }
}
