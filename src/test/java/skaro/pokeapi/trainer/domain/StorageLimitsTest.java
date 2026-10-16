package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageLimitsTest {

    @Test
    void exposesConfiguredLimits() {
        StorageLimits limits = new StorageLimits(6, 30);
        assertEquals(6, limits.getPartyMax());
        assertEquals(30, limits.getBoxMax());
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(0, 30));
        assertThrows(IllegalArgumentException.class, () -> new StorageLimits(6, -1));
    }
}
