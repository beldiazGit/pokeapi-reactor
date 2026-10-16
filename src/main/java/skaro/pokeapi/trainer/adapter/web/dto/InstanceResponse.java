package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InstanceResponse {
    public UUID id;
    public String species;
    public String nickname;
    public Map<String, Integer> ivs;
    public Map<String, Integer> evs;
    public String nature;
    public String ability;
    public boolean shiny;
    public String gender;
    public List<String> moves;
    public String heldItem;
    public String originalTrainerId;
    public String pokeball;
    public String captureDate;
    public int initialLevel;
    public String originLocation;
}
