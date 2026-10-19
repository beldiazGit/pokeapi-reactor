package skaro.pokeapi.trainer.adapter.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.adapter.web.dto.CaptureRequest;
import skaro.pokeapi.trainer.adapter.web.dto.CaptureResponse;
import skaro.pokeapi.trainer.adapter.web.dto.DtoMapper;
import skaro.pokeapi.trainer.adapter.web.dto.EvolveRequest;
import skaro.pokeapi.trainer.adapter.web.dto.InstanceResponse;
import skaro.pokeapi.trainer.adapter.web.dto.MoveRequest;
import skaro.pokeapi.trainer.adapter.web.dto.PartyMemberResponse;
import skaro.pokeapi.trainer.application.TrainerService;

@RestController
@RequestMapping("/api/v1/trainers/{trainerId}")
@Tag(name = "Trainer Pokémon", description = "Lifecycle of a trainer's Pokémon: capture, party/box, transfer, evolution")
public class TrainerPokemonController {

    private final TrainerService service;

    public TrainerPokemonController(TrainerService service) {
        this.service = service;
    }

    @PostMapping("/pokemon")
    @Operation(summary = "Capture a Pokémon",
            description = "Creates a new Pokémon instance owned by the trainer, placing it in the active party "
                    + "or the storage box depending on availability.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pokémon captured and stored; `Location` header "
                    + "points to the new instance"),
            @ApiResponse(responseCode = "422", description = "Capture request is invalid (unknown species, "
                    + "invalid ability/moveset/stats, etc.)", content = @Content(
                            mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "409", description = "Both the active party and the storage box are full",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Mono<ResponseEntity<CaptureResponse>> capture(
            @Parameter(description = "Identifier of the trainer capturing the Pokémon", example = "ash-ketchum")
            @PathVariable String trainerId,
            @RequestBody CaptureRequest request) {
        return service.capture(trainerId, DtoMapper.toCommand(request))
                .map(result -> {
                    URI location = URI.create(String.format(
                            "/api/v1/trainers/%s/pokemon/%s", trainerId, result.getInstanceId()));
                    return ResponseEntity.created(location)
                            .body(new CaptureResponse(result.getInstanceId(), result.getLocation().name()));
                });
    }

    @GetMapping("/pokemon/{id}")
    @Operation(summary = "Get a Pokémon instance",
            description = "Retrieves the full detail of a single Pokémon instance owned by the trainer, "
                    + "regardless of whether it is in the active party or the storage box.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokémon instance found"),
            @ApiResponse(responseCode = "404", description = "Trainer or Pokémon instance not found",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Mono<InstanceResponse> getInstance(
            @Parameter(description = "Identifier of the trainer who owns the Pokémon", example = "ash-ketchum")
            @PathVariable String trainerId,
            @Parameter(description = "Identifier of the Pokémon instance",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return service.getInstance(trainerId, id).map(DtoMapper::toInstanceResponse);
    }

    @GetMapping("/party")
    @Operation(summary = "List the active party",
            description = "Returns the composite view of the trainer's active party, combining each Pokémon "
                    + "instance with its species catalog data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active party members returned (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Trainer not found",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Flux<PartyMemberResponse> listParty(
            @Parameter(description = "Identifier of the trainer", example = "ash-ketchum")
            @PathVariable String trainerId) {
        return service.listParty(trainerId).map(DtoMapper::toPartyMemberResponse);
    }

    @GetMapping("/box")
    @Operation(summary = "List the storage box",
            description = "Returns every Pokémon instance currently kept in the trainer's storage box.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Box contents returned (may be empty)"),
            @ApiResponse(responseCode = "404", description = "Trainer not found",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Flux<InstanceResponse> listBox(
            @Parameter(description = "Identifier of the trainer", example = "ash-ketchum")
            @PathVariable String trainerId) {
        return service.listBox(trainerId).map(DtoMapper::toInstanceResponse);
    }

    @PatchMapping("/pokemon/{id}")
    @Operation(summary = "Move a Pokémon between party and box",
            description = "Transfers a Pokémon instance between the active party and the storage box.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokémon moved; updated instance returned"),
            @ApiResponse(responseCode = "404", description = "Trainer or Pokémon instance not found",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "409", description = "Destination (party or box) is full",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Mono<InstanceResponse> move(
            @Parameter(description = "Identifier of the trainer who owns the Pokémon", example = "ash-ketchum")
            @PathVariable String trainerId,
            @Parameter(description = "Identifier of the Pokémon instance to move",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody MoveRequest request) {
        return service.move(trainerId, id, request.location)
                .then(service.getInstance(trainerId, id))
                .map(DtoMapper::toInstanceResponse);
    }

    @PostMapping("/pokemon/{id}/evolve")
    @Operation(summary = "Evolve a Pokémon",
            description = "Evolves a Pokémon instance into the requested target species, "
                    + "if that evolution is valid for its current species and state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pokémon evolved; updated instance returned"),
            @ApiResponse(responseCode = "404", description = "Trainer or Pokémon instance not found",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "Requested evolution is not valid for this Pokémon",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public Mono<InstanceResponse> evolve(
            @Parameter(description = "Identifier of the trainer who owns the Pokémon", example = "ash-ketchum")
            @PathVariable String trainerId,
            @Parameter(description = "Identifier of the Pokémon instance to evolve",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody EvolveRequest request) {
        return service.evolve(trainerId, id, request.targetSpecies).map(DtoMapper::toInstanceResponse);
    }
}
