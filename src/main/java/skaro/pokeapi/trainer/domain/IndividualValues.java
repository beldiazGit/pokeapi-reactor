package skaro.pokeapi.trainer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;

public final class IndividualValues {

    public static final int MIN = 0;
    public static final int MAX = 31;

    private final Map<Stat, Integer> values;

    public IndividualValues(Map<Stat, Integer> values) {
        EnumMap<Stat, Integer> copy = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            Integer value = values == null ? null : values.get(stat);
            if (value == null) {
                throw new InvalidStatValueException("Falta el IV de " + stat);
            }
            if (value < MIN || value > MAX) {
                throw new InvalidStatValueException(
                        "IV de " + stat + " fuera de rango [" + MIN + "," + MAX + "]: " + value);
            }
            copy.put(stat, value);
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public int get(Stat stat) {
        return values.get(stat);
    }

    public Map<Stat, Integer> asMap() {
        return values;
    }
}
