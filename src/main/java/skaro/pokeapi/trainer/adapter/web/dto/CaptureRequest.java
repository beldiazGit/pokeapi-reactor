package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;

public class CaptureRequest {
    public String species;
    public String nickname;
    public Map<Stat, Integer> ivs;
    public Map<Stat, Integer> evs;
    public String nature;
    public String ability;
    public boolean shiny;
    public Gender gender;
    public List<String> moves;
    public String heldItem;
    public String originalTrainerId;
    public String pokeball;
    public int initialLevel;
    public String originLocation;
}
