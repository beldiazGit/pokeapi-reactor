package skaro.pokeapi.trainer.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class EvolveRequest {

    @Schema(description = "PokeAPI species name the Pokémon should evolve into", example = "ivysaur")
    public String targetSpecies;
}
