package skaro.pokeapi.trainer.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PokemonInstance {

    private final UUID id;
    private final SpeciesRef species;
    private final String nickname;
    private final IndividualValues ivs;
    private final EffortValues evs;
    private final Nature nature;
    private final String ability;
    private final boolean shiny;
    private final Gender gender;
    private final Moveset moveset;
    private final HeldItem heldItem;
    private final OriginMetadata origin;

    private PokemonInstance(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.species = Objects.requireNonNull(b.species, "species");
        this.nickname = b.nickname;
        this.ivs = Objects.requireNonNull(b.ivs, "ivs");
        this.evs = Objects.requireNonNull(b.evs, "evs");
        this.nature = Objects.requireNonNull(b.nature, "nature");
        this.ability = Objects.requireNonNull(b.ability, "ability");
        this.shiny = b.shiny;
        this.gender = Objects.requireNonNull(b.gender, "gender");
        this.moveset = Objects.requireNonNull(b.moveset, "moveset");
        this.heldItem = b.heldItem;
        this.origin = Objects.requireNonNull(b.origin, "origin");
    }

    public PokemonInstance evolve(SpeciesRef newSpecies, String newAbility) {
        return toBuilder()
                .species(Objects.requireNonNull(newSpecies, "newSpecies"))
                .ability(Objects.requireNonNull(newAbility, "newAbility"))
                .build();
    }

    public UUID getId() { return id; }
    public SpeciesRef getSpecies() { return species; }
    public String getNickname() { return nickname; }
    public IndividualValues getIvs() { return ivs; }
    public EffortValues getEvs() { return evs; }
    public Nature getNature() { return nature; }
    public String getAbility() { return ability; }
    public boolean isShiny() { return shiny; }
    public Gender getGender() { return gender; }
    public Moveset getMoveset() { return moveset; }
    public Optional<HeldItem> getHeldItem() { return Optional.ofNullable(heldItem); }
    public OriginMetadata getOrigin() { return origin; }

    public static Builder builder() { return new Builder(); }

    private Builder toBuilder() {
        return new Builder()
                .id(id).species(species).nickname(nickname).ivs(ivs).evs(evs)
                .nature(nature).ability(ability).shiny(shiny).gender(gender)
                .moveset(moveset).heldItem(heldItem).origin(origin);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PokemonInstance)) return false;
        return id.equals(((PokemonInstance) o).id);
    }
    @Override public int hashCode() { return Objects.hash(id); }

    public static final class Builder {
        private UUID id;
        private SpeciesRef species;
        private String nickname;
        private IndividualValues ivs;
        private EffortValues evs;
        private Nature nature;
        private String ability;
        private boolean shiny;
        private Gender gender;
        private Moveset moveset;
        private HeldItem heldItem;
        private OriginMetadata origin;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder species(SpeciesRef species) { this.species = species; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder ivs(IndividualValues ivs) { this.ivs = ivs; return this; }
        public Builder evs(EffortValues evs) { this.evs = evs; return this; }
        public Builder nature(Nature nature) { this.nature = nature; return this; }
        public Builder ability(String ability) { this.ability = ability; return this; }
        public Builder shiny(boolean shiny) { this.shiny = shiny; return this; }
        public Builder gender(Gender gender) { this.gender = gender; return this; }
        public Builder moveset(Moveset moveset) { this.moveset = moveset; return this; }
        public Builder heldItem(HeldItem heldItem) { this.heldItem = heldItem; return this; }
        public Builder origin(OriginMetadata origin) { this.origin = origin; return this; }

        public PokemonInstance build() { return new PokemonInstance(this); }
    }
}
