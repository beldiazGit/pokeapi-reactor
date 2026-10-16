package skaro.pokeapi.trainer.application;

import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.Stat;

public class CaptureCommand {
    private String species;
    private String nickname;
    private Map<Stat, Integer> ivs;
    private Map<Stat, Integer> evs;
    private String nature;
    private String ability;
    private boolean shiny;
    private Gender gender;
    private List<String> moves;
    private String heldItem;
    private String originalTrainerId;
    private String pokeball;
    private int initialLevel;
    private String originLocation;

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Map<Stat, Integer> getIvs() { return ivs; }
    public void setIvs(Map<Stat, Integer> ivs) { this.ivs = ivs; }
    public Map<Stat, Integer> getEvs() { return evs; }
    public void setEvs(Map<Stat, Integer> evs) { this.evs = evs; }
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getAbility() { return ability; }
    public void setAbility(String ability) { this.ability = ability; }
    public boolean isShiny() { return shiny; }
    public void setShiny(boolean shiny) { this.shiny = shiny; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public List<String> getMoves() { return moves; }
    public void setMoves(List<String> moves) { this.moves = moves; }
    public String getHeldItem() { return heldItem; }
    public void setHeldItem(String heldItem) { this.heldItem = heldItem; }
    public String getOriginalTrainerId() { return originalTrainerId; }
    public void setOriginalTrainerId(String originalTrainerId) { this.originalTrainerId = originalTrainerId; }
    public String getPokeball() { return pokeball; }
    public void setPokeball(String pokeball) { this.pokeball = pokeball; }
    public int getInitialLevel() { return initialLevel; }
    public void setInitialLevel(int initialLevel) { this.initialLevel = initialLevel; }
    public String getOriginLocation() { return originLocation; }
    public void setOriginLocation(String originLocation) { this.originLocation = originLocation; }
}
