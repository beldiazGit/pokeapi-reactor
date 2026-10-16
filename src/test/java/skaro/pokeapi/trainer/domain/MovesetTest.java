package skaro.pokeapi.trainer.domain;

import org.junit.jupiter.api.Test;
import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MovesetTest {

    @Test
    void acceptsUpToFourUniqueMoves() {
        assertEquals(3, new Moveset(Arrays.asList("tackle", "growl", "vine-whip")).getMoves().size());
    }

    @Test
    void acceptsEmptyMoveset() {
        assertTrue(new Moveset(Collections.emptyList()).getMoves().isEmpty());
    }

    @Test
    void rejectsMoreThanFour() {
        assertThrows(InvalidMovesetException.class,
                () -> new Moveset(Arrays.asList("a", "b", "c", "d", "e")));
    }

    @Test
    void rejectsDuplicatesCaseInsensitive() {
        assertThrows(InvalidMovesetException.class,
                () -> new Moveset(Arrays.asList("Tackle", "tackle")));
    }
}
