package skaro.pokeapi.trainer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;

public final class Moveset {

    public static final int MAX_MOVES = 4;

    private final List<String> moves;

    public Moveset(List<String> moves) {
        List<String> source = moves == null ? Collections.emptyList() : moves;
        if (source.size() > MAX_MOVES) {
            throw new InvalidMovesetException("A Pokemon cannot have more than " + MAX_MOVES + " moves");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String move : source) {
            if (move == null || move.trim().isEmpty()) {
                throw new InvalidMovesetException("Blank move in the set");
            }
            String n = move.trim().toLowerCase();
            if (!seen.add(n)) {
                throw new InvalidMovesetException("Duplicate move: " + n);
            }
            normalized.add(n);
        }
        this.moves = Collections.unmodifiableList(normalized);
    }

    public List<String> getMoves() { return moves; }
}
