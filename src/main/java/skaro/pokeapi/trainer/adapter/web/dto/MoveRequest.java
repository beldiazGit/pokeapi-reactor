package skaro.pokeapi.trainer.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import skaro.pokeapi.trainer.domain.StorageLocation;

public class MoveRequest {

    @Schema(description = "Destination for the Pokémon instance: the active party or the storage box",
            example = "PARTY")
    public StorageLocation location;
}
