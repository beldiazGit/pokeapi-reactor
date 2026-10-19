package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public class InstanceResponse {

    @Schema(description = "Identifier of the Pokémon instance", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID id;

    @Schema(description = "PokeAPI species name", example = "bulbasaur")
    public String species;

    @Schema(description = "Nickname given to the Pokémon, if any", example = "Bulby")
    public String nickname;

    @Schema(description = "Individual values (IVs) per stat")
    public Map<String, Integer> ivs;

    @Schema(description = "Effort values (EVs) per stat")
    public Map<String, Integer> evs;

    @Schema(description = "Nature name", example = "modest")
    public String nature;

    @Schema(description = "Ability name", example = "overgrow")
    public String ability;

    @Schema(description = "Whether the Pokémon is shiny", example = "false")
    public boolean shiny;

    @Schema(description = "Gender of the Pokémon", example = "MALE")
    public String gender;

    @Schema(description = "Known moves", example = "[\"tackle\", \"growl\"]")
    public List<String> moves;

    @Schema(description = "Item currently held by the Pokémon, if any", example = "oran-berry")
    public String heldItem;

    @Schema(description = "Identifier of the trainer who originally captured the Pokémon", example = "ash-ketchum")
    public String originalTrainerId;

    @Schema(description = "Poké Ball used for the capture", example = "poke-ball")
    public String pokeball;

    @Schema(description = "ISO-8601 timestamp of capture", example = "2024-05-01T10:15:30Z")
    public String captureDate;

    @Schema(description = "Level of the Pokémon at the time of capture", example = "5")
    public int initialLevel;

    @Schema(description = "Location where the Pokémon was captured", example = "route-1")
    public String originLocation;
}
