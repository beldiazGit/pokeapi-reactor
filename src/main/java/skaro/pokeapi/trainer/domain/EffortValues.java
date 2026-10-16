package skaro.pokeapi.trainer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;

public final class EffortValues {

    public static final int MIN_PER_STAT = 0;
    public static final int MAX_PER_STAT = 252;
    public static final int MAX_TOTAL = 510;

    private final Map<Stat, Integer> values;

    public EffortValues(Map<Stat, Integer> values) {
        EnumMap<Stat, Integer> copy = new EnumMap<>(Stat.class);
        int total = 0;
        for (Stat stat : Stat.values()) {
            Integer value = values == null ? null : values.get(stat);
            if (value == null) {
                throw new InvalidEffortValuesException("Missing EV for " + stat);
            }
            if (value < MIN_PER_STAT || value > MAX_PER_STAT) {
                throw new InvalidEffortValuesException(
                        "EV for " + stat + " out of range [" + MIN_PER_STAT + "," + MAX_PER_STAT + "]: " + value);
            }
            total += value;
            copy.put(stat, value);
        }
        if (total > MAX_TOTAL) {
            throw new InvalidEffortValuesException("EV total " + total + " exceeds the maximum " + MAX_TOTAL);
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
