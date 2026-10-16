package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.UUID;

public class CaptureResponse {
    public UUID instanceId;
    public String location;

    public CaptureResponse(UUID instanceId, String location) {
        this.instanceId = instanceId;
        this.location = location;
    }
}
