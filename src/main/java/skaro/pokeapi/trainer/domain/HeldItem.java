package skaro.pokeapi.trainer.domain;

public final class HeldItem {
    private final String name;

    public HeldItem(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Held item cannot be blank");
        }
        this.name = name.trim().toLowerCase();
    }

    public String getName() { return name; }
}
