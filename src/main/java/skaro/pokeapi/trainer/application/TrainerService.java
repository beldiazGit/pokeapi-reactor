package skaro.pokeapi.trainer.application;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import skaro.pokeapi.trainer.application.port.OptimisticLockException;
import skaro.pokeapi.trainer.application.port.PokemonCatalogPort;
import skaro.pokeapi.trainer.application.port.PokemonRepository;
import skaro.pokeapi.trainer.application.port.SpeciesData;
import skaro.pokeapi.trainer.domain.EffortValues;
import skaro.pokeapi.trainer.domain.Gender;
import skaro.pokeapi.trainer.domain.HeldItem;
import skaro.pokeapi.trainer.domain.IndividualValues;
import skaro.pokeapi.trainer.domain.Moveset;
import skaro.pokeapi.trainer.domain.Nature;
import skaro.pokeapi.trainer.domain.OriginMetadata;
import skaro.pokeapi.trainer.domain.PokemonInstance;
import skaro.pokeapi.trainer.domain.SpeciesRef;
import skaro.pokeapi.trainer.domain.StorageLocation;
import skaro.pokeapi.trainer.domain.Trainer;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;
import skaro.pokeapi.trainer.domain.exception.InvalidEvolutionException;
import skaro.pokeapi.trainer.domain.exception.SpeciesNotFoundException;
import skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException;

@Service
public class TrainerService {

    // Max optimistic-lock retries for a mutating use case before surfacing the
    // conflict. Under contention a retry reloads a fresh copy and re-applies the
    // same change; 8 attempts is ample headroom for the expected concurrency.
    private static final int MAX_RETRIES = 8;

    private final PokemonRepository repository;
    private final PokemonCatalogPort catalog;

    public TrainerService(PokemonRepository repository, PokemonCatalogPort catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    public Mono<CaptureResult> capture(String trainerId, CaptureCommand cmd) {
        String species = cmd.getSpecies() == null ? "" : cmd.getSpecies().trim().toLowerCase();
        String ability = cmd.getAbility() == null ? "" : cmd.getAbility().trim().toLowerCase();
        return catalog.getSpecies(species)
                .switchIfEmpty(Mono.error(new SpeciesNotFoundException(species)))
                .flatMap(speciesData -> {
                    validateAbility(ability, speciesData);
                    // Build the instance (with its UUID) ONCE, outside the retried
                    // block: a retry must re-apply the SAME instance, not mint a new one.
                    PokemonInstance instance = toInstance(cmd, ability);
                    return Mono.defer(() -> repository.loadOrCreate(trainerId)
                            .flatMap(trainer -> {
                                StorageLocation location = trainer.capture(instance);
                                return repository.save(trainer)
                                        .thenReturn(new CaptureResult(instance.getId(), location));
                            }))
                            .retryWhen(optimisticLockRetry());
                });
    }

    public Mono<PokemonInstance> getInstance(String trainerId, UUID id) {
        return requireTrainer(trainerId).map(trainer -> trainer.findById(id));
    }

    public Flux<PartyMember> listParty(String trainerId) {
        return requireTrainer(trainerId)
                .flatMapMany(trainer -> Flux.fromIterable(trainer.party()))
                .flatMap(instance -> catalog.getSpecies(instance.getSpecies().getName())
                        .switchIfEmpty(Mono.error(new SpeciesNotFoundException(instance.getSpecies().getName())))
                        .map(species -> new PartyMember(instance, species)));
    }

    public Flux<PokemonInstance> listBox(String trainerId) {
        return requireTrainer(trainerId)
                .flatMapMany(trainer -> Flux.fromIterable(trainer.box()));
    }

    public Mono<StorageLocation> move(String trainerId, UUID id, StorageLocation target) {
        if (target == null) {
            return Mono.error(new IllegalArgumentException("location is required"));
        }
        return Mono.defer(() -> requireTrainer(trainerId).flatMap(trainer -> {
            if (target == StorageLocation.PARTY) {
                trainer.moveToParty(id);
            } else {
                trainer.moveToBox(id);
            }
            return repository.save(trainer).thenReturn(trainer.locationOf(id));
        })).retryWhen(optimisticLockRetry());
    }

    public Mono<PokemonInstance> evolve(String trainerId, UUID id, String targetSpecies) {
        String target = targetSpecies == null ? "" : targetSpecies.trim().toLowerCase();
        // Resolve the evolution option + new ability ONCE, outside the retried
        // block (it is a pure read against the catalog and does not change under
        // concurrency). Only the load -> mutate -> save portion is retried.
        return requireTrainer(trainerId).flatMap(trainer -> {
            PokemonInstance current = trainer.findById(id);
            String from = current.getSpecies().getName();
            return catalog.directEvolutionsOf(from)
                    .filter(option -> option.getToSpecies().equalsIgnoreCase(target))
                    .next()
                    .switchIfEmpty(Mono.error(new InvalidEvolutionException(from, target)))
                    .map(option -> option.getAllowedAbilities().isEmpty()
                            ? current.getAbility() : option.getAllowedAbilities().get(0));
        }).flatMap(newAbility -> Mono.defer(() -> requireTrainer(trainerId).flatMap(trainer -> {
            trainer.evolve(id, new SpeciesRef(target), newAbility);
            return repository.save(trainer).thenReturn(trainer.findById(id));
        })).retryWhen(optimisticLockRetry()));
    }

    /**
     * Retry policy for the mutating use cases: retry up to MAX_RETRIES times only
     * on OptimisticLockException, and when retries are exhausted rethrow the
     * underlying OptimisticLockException (not Reactor's RetryExhaustedException
     * wrapper) so it maps cleanly to 409 at the web layer.
     */
    private static Retry optimisticLockRetry() {
        return Retry.max(MAX_RETRIES)
                .filter(OptimisticLockException.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private Mono<Trainer> requireTrainer(String trainerId) {
        return repository.findByTrainerId(trainerId)
                .switchIfEmpty(Mono.error(new TrainerNotFoundException(trainerId)));
    }

    private void validateAbility(String normalizedAbility, SpeciesData species) {
        boolean belongs = species.getAbilities().stream()
                .anyMatch(a -> a.equalsIgnoreCase(normalizedAbility));
        if (!belongs) {
            throw new InvalidAbilityException(normalizedAbility, species.getName());
        }
    }

    private PokemonInstance toInstance(CaptureCommand cmd, String normalizedAbility) {
        HeldItem heldItem = (cmd.getHeldItem() == null || cmd.getHeldItem().trim().isEmpty())
                ? null : new HeldItem(cmd.getHeldItem());
        return PokemonInstance.builder()
                .id(UUID.randomUUID())
                .species(new SpeciesRef(cmd.getSpecies()))
                .nickname(cmd.getNickname())
                .ivs(new IndividualValues(cmd.getIvs()))
                .evs(new EffortValues(cmd.getEvs()))
                .nature(new Nature(cmd.getNature()))
                .ability(normalizedAbility)
                .shiny(cmd.isShiny())
                .gender(cmd.getGender() == null ? Gender.GENDERLESS : cmd.getGender())
                .moveset(new Moveset(cmd.getMoves()))
                .heldItem(heldItem)
                .origin(new OriginMetadata(cmd.getOriginalTrainerId(), cmd.getPokeball(),
                        LocalDate.now(), cmd.getInitialLevel(), cmd.getOriginLocation()))
                .build();
    }
}
