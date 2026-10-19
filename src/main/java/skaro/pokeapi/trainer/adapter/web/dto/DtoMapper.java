package skaro.pokeapi.trainer.adapter.web.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import skaro.pokeapi.trainer.application.CaptureCommand;
import skaro.pokeapi.trainer.application.PartyMember;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.Stat;

public final class DtoMapper {

    private DtoMapper() {}

    public static CaptureCommand toCommand(CaptureRequest req) {
        CaptureCommand cmd = new CaptureCommand();
        cmd.setSpecies(req.species);
        cmd.setNickname(req.nickname);
        cmd.setIvs(req.ivs);
        cmd.setEvs(req.evs);
        cmd.setNature(req.nature);
        cmd.setAbility(req.ability);
        cmd.setShiny(req.shiny);
        cmd.setGender(req.gender);
        cmd.setMoves(req.moves);
        cmd.setHeldItem(req.heldItem);
        cmd.setOriginalTrainerId(req.originalTrainerId);
        cmd.setPokeball(req.pokeball);
        cmd.setInitialLevel(req.initialLevel);
        cmd.setOriginLocation(req.originLocation);
        return cmd;
    }

    public static InstanceResponse toInstanceResponse(PokemonInstance p) {
        InstanceResponse r = new InstanceResponse();
        r.id = p.getId();
        r.species = p.getSpecies().getName();
        r.nickname = p.getNickname();
        r.ivs = statMap(p.getIvs().asMap());
        r.evs = statMap(p.getEvs().asMap());
        r.nature = p.getNature().getName();
        r.ability = p.getAbility();
        r.shiny = p.isShiny();
        r.gender = p.getGender().name();
        r.moves = new ArrayList<>(p.getMoveset().getMoves());
        r.heldItem = p.getHeldItem().map(h -> h.getName()).orElse(null);
        r.originalTrainerId = p.getOrigin().getOriginalTrainerId();
        r.pokeball = p.getOrigin().getPokeball();
        r.captureDate = p.getOrigin().getCaptureDate() == null ? null : p.getOrigin().getCaptureDate().toString();
        r.initialLevel = p.getOrigin().getInitialLevel();
        r.originLocation = p.getOrigin().getOriginLocation();
        return r;
    }

    public static PartyMemberResponse toPartyMemberResponse(PartyMember member) {
        PartyMemberResponse r = new PartyMemberResponse();
        r.instance = toInstanceResponse(member.getInstance());
        SpeciesData s = member.getSpecies();
        PartyMemberResponse.SpeciesView view = new PartyMemberResponse.SpeciesView();
        view.name = s.getName();
        view.types = s.getTypes();
        view.baseStats = statMap(s.getBaseStats());
        view.spriteUrl = s.getSpriteUrl();
        r.species = view;
        return r;
    }

    private static Map<String, Integer> statMap(Map<Stat, Integer> source) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Stat s : Stat.values()) {
            if (source.containsKey(s)) out.put(s.name(), source.get(s));
        }
        return out;
    }
}
