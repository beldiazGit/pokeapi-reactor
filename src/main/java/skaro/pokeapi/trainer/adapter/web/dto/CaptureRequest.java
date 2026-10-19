package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;

public class CaptureRequest {

    @Schema(description = "PokeAPI species name to capture", example = "bulbasaur")
    public String species;

    @Schema(description = "Optional nickname given to the Pokémon", example = "Bulby")
    public String nickname;

    @Schema(description = "Individual values (IVs) per stat")
    public Map<Stat, Integer> ivs;

    @Schema(description = "Effort values (EVs) per stat")
    public Map<Stat, Integer> evs;

    @Schema(description = "Nature name", example = "modest")
    public String nature;

    @Schema(description = "Ability name", example = "overgrow")
    public String ability;

    @Schema(description = "Whether the captured Pokémon is shiny", example = "false")
    public boolean shiny;

    @Schema(description = "Gender of the captured Pokémon", example = "MALE")
    public Gender gender;

    @Schema(description = "Known moves at the time of capture", example = "[\"tackle\", \"growl\"]")
    public List<String> moves;

    @Schema(description = "Item held by the Pokémon, if any", example = "oran-berry")
    public String heldItem;

    @Schema(description = "Identifier of the trainer who originally captured the Pokémon", example = "ash-ketchum")
    public String originalTrainerId;

    @Schema(description = "Poké Ball used for the capture", example = "poke-ball")
    public String pokeball;

    @Schema(description = "Level of the Pokémon at the time of capture", example = "5")
    public int initialLevel;

    @Schema(description = "Location where the Pokémon was captured", example = "route-1")
    public String originLocation;
}
