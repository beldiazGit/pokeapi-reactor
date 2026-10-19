package skaro.pokeapi.trainer.application;

import java.util.UUID;

import skaro.pokeapi.trainer.domain.StorageLocation;

public class CaptureResult {
    private final UUID instanceId;
    private final StorageLocation location;

    public CaptureResult(UUID instanceId, StorageLocation location) {
        this.instanceId = instanceId;
        this.location = location;
    }

    public UUID getInstanceId() { return instanceId; }
    public StorageLocation getLocation() { return location; }
}
