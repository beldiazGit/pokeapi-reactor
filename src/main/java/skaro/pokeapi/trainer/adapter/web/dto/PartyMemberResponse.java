package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

public class PartyMemberResponse {

    @Schema(description = "Full detail of the Pokémon instance in the active party")
    public InstanceResponse instance;

    @Schema(description = "Species catalog data (from PokeAPI) for this party member")
    public SpeciesView species;

    public static class SpeciesView {

        @Schema(description = "PokeAPI species name", example = "bulbasaur")
        public String name;

        @Schema(description = "Elemental types of the species", example = "[\"grass\", \"poison\"]")
        public List<String> types;

        @Schema(description = "Base stats of the species")
        public Map<String, Integer> baseStats;

        @Schema(description = "URL of the species sprite image",
                example = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png")
        public String spriteUrl;
    }
}
