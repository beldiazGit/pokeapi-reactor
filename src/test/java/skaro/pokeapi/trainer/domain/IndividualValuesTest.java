package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndividualValuesTest {

    private Map<Stat, Integer> valid(int value) {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) m.put(s, value);
        return m;
    }

    @Test
    void acceptsValuesInRange() {
        IndividualValues ivs = new IndividualValues(valid(31));
        assertEquals(31, ivs.get(Stat.SPEED));
    }

    @Test
    void rejectsValueAbove31() {
        Map<Stat, Integer> m = valid(10);
        m.put(Stat.ATTACK, 32);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }

    @Test
    void rejectsNegativeValue() {
        Map<Stat, Integer> m = valid(10);
        m.put(Stat.HP, -1);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }

    @Test
    void rejectsMissingStat() {
        Map<Stat, Integer> m = new EnumMap<>(Stat.class);
        m.put(Stat.HP, 10);
        assertThrows(InvalidStatValueException.class, () -> new IndividualValues(m));
    }
}
