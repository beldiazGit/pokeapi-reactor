package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainerTest {

    private Trainer trainer;

    @BeforeEach
    void setUp() {
        trainer = new Trainer("ash", new StorageLimits(2, 2));
    }

    private PokemonInstance instance(String species) {
        Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) { ivs.put(s, 31); evs.put(s, 0); }
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef(species))
                .ivs(new IndividualValues(ivs))
                .evs(new EffortValues(evs))
                .nature(new Nature("hardy"))
                .ability("overgrow")
                .gender(Gender.MALE)
                .moveset(new Moveset(Collections.emptyList()))
                .origin(new OriginMetadata("ash", "poke-ball", LocalDate.now(), 5, "pallet-town"))
                .build();
    }

    @Test
    void capturePlacesInPartyWhileThereIsRoom() {
        PokemonInstance p = instance("bulbasaur");
        assertEquals(StorageLocation.PARTY, trainer.capture(p));
        assertEquals(1, trainer.party().size());
    }

    @Test
    void captureOverflowsToBoxWhenPartyIsFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        assertEquals(StorageLocation.BOX, trainer.capture(instance("c")));
        assertEquals(2, trainer.party().size());
        assertEquals(1, trainer.box().size());
    }

    @Test
    void captureThrowsWhenPartyAndBoxFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        trainer.capture(instance("c"));
        trainer.capture(instance("d"));
        assertThrows(StorageFullException.class, () -> trainer.capture(instance("e")));
    }

    @Test
    void moveToBoxThenBackToPartyPreservesInstance() {
        PokemonInstance p = instance("pikachu");
        trainer.capture(p);
        trainer.moveToBox(p.getId());
        assertEquals(StorageLocation.BOX, trainer.locationOf(p.getId()));
        trainer.moveToParty(p.getId());
        assertEquals(StorageLocation.PARTY, trainer.locationOf(p.getId()));
    }

    @Test
    void moveToPartyThrowsWhenPartyFull() {
        trainer.capture(instance("a"));
        trainer.capture(instance("b"));
        PokemonInstance boxed = instance("c");
        trainer.capture(boxed); // overflow to box
        assertThrows(PartyFullException.class, () -> trainer.moveToParty(boxed.getId()));
    }

    @Test
    void findByIdThrowsWhenAbsent() {
        assertThrows(InstanceNotFoundException.class, () -> trainer.findById(UUID.randomUUID()));
    }

    @Test
    void evolvePreservesPartySlot() {
        PokemonInstance first = instance("a");
        PokemonInstance magikarp = instance("magikarp");
        trainer.capture(first);
        trainer.capture(magikarp);
        trainer.evolve(magikarp.getId(), new SpeciesRef("gyarados"), "intimidate");

        assertEquals("gyarados", trainer.party().get(1).getSpecies().getName());
        assertEquals("intimidate", trainer.party().get(1).getAbility());
        assertEquals(magikarp.getId(), trainer.party().get(1).getId());
    }
}
