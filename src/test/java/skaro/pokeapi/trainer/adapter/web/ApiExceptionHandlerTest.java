package skaro.pokeapi.trainer.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.TrainerService;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;
import skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TrainerPokemonController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private TrainerService service;

    @Test
    void storageFullMapsTo409() {
        when(service.capture(eq("ash"), any())).thenReturn(Mono.error(new StorageFullException()));
        webClient.post().uri("/api/v1/trainers/ash/pokemon")
                .header("Content-Type", "application/json")
                .bodyValue("{\"species\":\"x\",\"ivs\":{},\"evs\":{}}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void trainerNotFoundMapsTo404() {
        UUID id = UUID.randomUUID();
        when(service.getInstance(eq("ghost"), eq(id)))
                .thenReturn(Mono.error(new TrainerNotFoundException("ghost")));
        webClient.get().uri("/api/v1/trainers/ghost/pokemon/" + id)
                .exchange()
                .expectStatus().isEqualTo(404);
    }
}
