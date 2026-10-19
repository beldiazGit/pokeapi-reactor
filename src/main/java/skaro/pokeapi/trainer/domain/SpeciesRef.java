package skaro.pokeapi.trainer.domain;

import java.util.Objects;

public final class SpeciesRef {
    private final String name;

    public SpeciesRef(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Species cannot be blank");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpeciesRef)) return false;
        return name.equals(((SpeciesRef) o).name);
    }
    @Override public int hashCode() { return Objects.hash(name); }
}
