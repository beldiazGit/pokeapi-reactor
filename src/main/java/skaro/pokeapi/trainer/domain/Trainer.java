package skaro.pokeapi.trainer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import skaro.pokeapi.trainer.domain.exception.BoxFullException;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;

public class Trainer {

    private final String trainerId;
    private final StorageLimits limits;
    // LinkedHashMap keyed by instance id: O(1) lookup by id while preserving
    // slot order. Re-inserting an existing key (evolve) keeps its position.
    private final Map<UUID, PokemonInstance> party = new LinkedHashMap<>();
    private final Map<UUID, PokemonInstance> box = new LinkedHashMap<>();

    public Trainer(String trainerId, StorageLimits limits) {
        if (trainerId == null || trainerId.trim().isEmpty()) {
            throw new IllegalArgumentException("trainerId cannot be blank");
        }
        this.trainerId = trainerId.trim();
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public StorageLocation capture(PokemonInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (party.size() < limits.getPartyMax()) {
            party.put(instance.getId(), instance);
            return StorageLocation.PARTY;
        }
        if (box.size() < limits.getBoxMax()) {
            box.put(instance.getId(), instance);
            return StorageLocation.BOX;
        }
        throw new StorageFullException();
    }

    public void moveToParty(UUID id) {
        if (party.containsKey(id)) return; // already in the party (idempotent)
        PokemonInstance instance = box.get(id);
        if (instance == null) {
            throw new InstanceNotFoundException(id);
        }
        if (party.size() >= limits.getPartyMax()) {
            throw new PartyFullException(limits.getPartyMax());
        }
        box.remove(id);
        party.put(id, instance);
    }

    public void moveToBox(UUID id) {
        if (box.containsKey(id)) return; // already in the box (idempotent)
        PokemonInstance instance = party.get(id);
        if (instance == null) {
            throw new InstanceNotFoundException(id);
        }
        if (box.size() >= limits.getBoxMax()) {
            throw new BoxFullException(limits.getBoxMax());
        }
        party.remove(id);
        box.put(id, instance);
    }

    public void evolve(UUID id, SpeciesRef newSpecies, String newAbility) {
        PokemonInstance partied = party.get(id);
        if (partied != null) {
            party.put(id, partied.evolve(newSpecies, newAbility));
            return;
        }
        PokemonInstance boxed = box.get(id);
        if (boxed != null) {
            box.put(id, boxed.evolve(newSpecies, newAbility));
            return;
        }
        throw new InstanceNotFoundException(id);
    }

    public PokemonInstance findById(UUID id) {
        PokemonInstance instance = party.get(id);
        if (instance != null) return instance;
        instance = box.get(id);
        if (instance != null) return instance;
        throw new InstanceNotFoundException(id);
    }

    public StorageLocation locationOf(UUID id) {
        if (party.containsKey(id)) return StorageLocation.PARTY;
        if (box.containsKey(id)) return StorageLocation.BOX;
        throw new InstanceNotFoundException(id);
    }

    public List<PokemonInstance> party() { return Collections.unmodifiableList(new ArrayList<>(party.values())); }
    public List<PokemonInstance> box() { return Collections.unmodifiableList(new ArrayList<>(box.values())); }
    public String getTrainerId() { return trainerId; }
}
