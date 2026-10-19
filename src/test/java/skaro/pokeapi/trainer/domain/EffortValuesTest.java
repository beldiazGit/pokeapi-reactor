package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EffortValuesTest {

    private Map<Stat, Integer> of(int hp, int atk, int def, int spa, int spd, int spe) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        m.put(Stat.HP, hp); m.put(Stat.ATTACK, atk); m.put(Stat.DEFENSE, def);
        m.put(Stat.SPECIAL_ATTACK, spa); m.put(Stat.SPECIAL_DEFENSE, spd); m.put(Stat.SPEED, spe);
        return m;
    }

    @Test
    void acceptsValidSpread() {
        EffortValues evs = new EffortValues(of(252, 252, 6, 0, 0, 0));
        assertEquals(252, evs.get(Stat.ATTACK));
    }

    @Test
    void rejectsSingleStatAbove252() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(253, 0, 0, 0, 0, 0)));
    }

    @Test
    void rejectsTotalAbove510() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(252, 252, 252, 0, 0, 0)));
    }

    @Test
    void rejectsNegative() {
        assertThrows(InvalidEffortValuesException.class, () -> new EffortValues(of(-1, 0, 0, 0, 0, 0)));
    }
}
