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
public class TrainerPokemonController {

    private final TrainerService service;

    public TrainerPokemonController(TrainerService service) {
        this.service = service;
    }

    @PostMapping("/pokemon")
    public Mono<ResponseEntity<CaptureResponse>> capture(
            @PathVariable String trainerId, @RequestBody CaptureRequest request) {
        return service.capture(trainerId, DtoMapper.toCommand(request))
                .map(result -> {
                    URI location = URI.create(String.format(
                            "/api/v1/trainers/%s/pokemon/%s", trainerId, result.getInstanceId()));
                    return ResponseEntity.created(location)
                            .body(new CaptureResponse(result.getInstanceId(), result.getLocation().name()));
                });
    }

    @GetMapping("/pokemon/{id}")
    public Mono<InstanceResponse> getInstance(@PathVariable String trainerId, @PathVariable UUID id) {
        return service.getInstance(trainerId, id).map(DtoMapper::toInstanceResponse);
    }

    @GetMapping("/party")
    public Flux<PartyMemberResponse> listParty(@PathVariable String trainerId) {
        return service.listParty(trainerId).map(DtoMapper::toPartyMemberResponse);
    }

    @GetMapping("/box")
    public Flux<InstanceResponse> listBox(@PathVariable String trainerId) {
        return service.listBox(trainerId).map(DtoMapper::toInstanceResponse);
    }

    @PatchMapping("/pokemon/{id}")
    public Mono<InstanceResponse> move(
            @PathVariable String trainerId, @PathVariable UUID id, @RequestBody MoveRequest request) {
        return service.move(trainerId, id, request.location)
                .then(service.getInstance(trainerId, id))
                .map(DtoMapper::toInstanceResponse);
    }

    @PostMapping("/pokemon/{id}/evolve")
    public Mono<InstanceResponse> evolve(
            @PathVariable String trainerId, @PathVariable UUID id, @RequestBody EvolveRequest request) {
        return service.evolve(trainerId, id, request.targetSpecies).map(DtoMapper::toInstanceResponse);
    }
}
