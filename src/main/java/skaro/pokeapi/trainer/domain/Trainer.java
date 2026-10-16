package skaro.pokeapi.trainer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import skaro.pokeapi.trainer.domain.exception.BoxFullException;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;

public class Trainer {

    private final String trainerId;
    private final StorageLimits limits;
    private final List<PokemonInstance> party = new ArrayList<>();
    private final List<PokemonInstance> box = new ArrayList<>();

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
            party.add(instance);
            return StorageLocation.PARTY;
        }
        if (box.size() < limits.getBoxMax()) {
            box.add(instance);
            return StorageLocation.BOX;
        }
        throw new StorageFullException();
    }

    public void moveToParty(UUID id) {
        int idx = indexIn(box, id);
        if (idx < 0) {
            if (indexIn(party, id) >= 0) return; // already in the party (idempotent)
            throw new InstanceNotFoundException(id);
        }
        if (party.size() >= limits.getPartyMax()) {
            throw new PartyFullException(limits.getPartyMax());
        }
        party.add(box.remove(idx));
    }

    public void moveToBox(UUID id) {
        int idx = indexIn(party, id);
        if (idx < 0) {
            if (indexIn(box, id) >= 0) return; // already in the box (idempotent)
            throw new InstanceNotFoundException(id);
        }
        if (box.size() >= limits.getBoxMax()) {
            throw new BoxFullException(limits.getBoxMax());
        }
        box.add(party.remove(idx));
    }

    public void evolve(UUID id, SpeciesRef newSpecies, String newAbility) {
        int partyIdx = indexIn(party, id);
        if (partyIdx >= 0) {
            party.set(partyIdx, party.get(partyIdx).evolve(newSpecies, newAbility));
            return;
        }
        int boxIdx = indexIn(box, id);
        if (boxIdx >= 0) {
            box.set(boxIdx, box.get(boxIdx).evolve(newSpecies, newAbility));
            return;
        }
        throw new InstanceNotFoundException(id);
    }

    public PokemonInstance findById(UUID id) {
        int p = indexIn(party, id);
        if (p >= 0) return party.get(p);
        int b = indexIn(box, id);
        if (b >= 0) return box.get(b);
        throw new InstanceNotFoundException(id);
    }

    public StorageLocation locationOf(UUID id) {
        if (indexIn(party, id) >= 0) return StorageLocation.PARTY;
        if (indexIn(box, id) >= 0) return StorageLocation.BOX;
        throw new InstanceNotFoundException(id);
    }

    public List<PokemonInstance> party() { return Collections.unmodifiableList(new ArrayList<>(party)); }
    public List<PokemonInstance> box() { return Collections.unmodifiableList(new ArrayList<>(box)); }
    public String getTrainerId() { return trainerId; }

    private int indexIn(List<PokemonInstance> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}
