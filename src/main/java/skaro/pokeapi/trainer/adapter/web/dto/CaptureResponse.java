package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public class CaptureResponse {

    @Schema(description = "Identifier assigned to the newly captured Pokémon instance",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID instanceId;

    @Schema(description = "Where the captured Pokémon was placed", example = "PARTY")
    public String location;

    public CaptureResponse(UUID instanceId, String location) {
        this.instanceId = instanceId;
        this.location = location;
    }
}
