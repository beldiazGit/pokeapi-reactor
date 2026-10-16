package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PokemonInstanceTest {

    private IndividualValues ivs(int v) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, v);
        return new IndividualValues(m);
    }

    private EffortValues evs() {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, 0);
        return new EffortValues(m);
    }

    private PokemonInstance magikarp() {
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef("magikarp"))
                .nickname("Splashy")
                .ivs(ivs(31))
                .evs(evs())
                .nature(new Nature("adamant"))
                .ability("swift-swim")
                .shiny(true)
                .gender(Gender.MALE)
                .moveset(new Moveset(Arrays.asList("splash")))
                .heldItem(new HeldItem("mystic-water"))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "route-1"))
                .build();
    }

    @Test
    void buildsWithAllFields() {
        PokemonInstance p = magikarp();
        assertEquals("magikarp", p.getSpecies().getName());
        assertTrue(p.isShiny());
        assertTrue(p.getHeldItem().isPresent());
        assertEquals("mystic-water", p.getHeldItem().get().getName());
    }

    @Test
    void evolvePreservesIdentityAndGeneticsButChangesSpeciesAndAbility() {
        PokemonInstance magikarp = magikarp();
        PokemonInstance gyarados = magikarp.evolve(new SpeciesRef("gyarados"), "intimidate");

        assertEquals(magikarp.getId(), gyarados.getId());
        assertEquals("gyarados", gyarados.getSpecies().getName());
        assertEquals("intimidate", gyarados.getAbility());
        assertEquals(magikarp.getIvs().get(Stat.SPEED), gyarados.getIvs().get(Stat.SPEED));
        assertEquals(magikarp.getNature(), gyarados.getNature());
        assertEquals(magikarp.getOrigin().getOriginalTrainerId(), gyarados.getOrigin().getOriginalTrainerId());
        assertEquals(magikarp.isShiny(), gyarados.isShiny());
    }

    @Test
    void requiresSpeciesAndId() {
        assertThrows(NullPointerException.class, () -> PokemonInstance.builder()
                .ivs(ivs(0)).evs(evs()).nature(new Nature("hardy")).ability("x")
                .gender(Gender.GENDERLESS).moveset(new Moveset(Collections.emptyList()))
                .origin(new OriginMetadata("ot", "ball", LocalDate.now(), 1, "lab"))
                .build());
    }
}
