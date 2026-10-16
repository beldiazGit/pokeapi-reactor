package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.List;
import java.util.Map;

public class PartyMemberResponse {
    public InstanceResponse instance;
    public SpeciesView species;

    public static class SpeciesView {
        public String name;
        public List<String> types;
        public Map<String, Integer> baseStats;
        public String spriteUrl;
    }
}
