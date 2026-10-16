package skaro.pokeapi.trainer.domain;

import java.util.Objects;

public final class Nature {
    private final String name;

    public Nature(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nature cannot be blank");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Nature)) return false;
        return name.equals(((Nature) o).name);
    }
    @Override public int hashCode() { return Objects.hash(name); }
}
