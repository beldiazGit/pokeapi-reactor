package skaro.pokeapi.trainer.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import skaro.pokeapi.trainer.application.CaptureResult;
import skaro.pokeapi.trainer.application.TrainerService;
import skaro.pokeapi.trainer.domain.StorageLocation;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TrainerPokemonController.class)
class TrainerPokemonControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private TrainerService service;

    @Test
    void captureReturns201WithLocationHeader() {
        UUID id = UUID.randomUUID();
        when(service.capture(eq("ash"), any()))
                .thenReturn(Mono.just(new CaptureResult(id, StorageLocation.PARTY)));

        String body = "{"
                + "\"species\":\"bulbasaur\",\"nature\":\"hardy\",\"ability\":\"overgrow\","
                + "\"gender\":\"MALE\",\"initialLevel\":5,"
                + "\"ivs\":{\"HP\":31,\"ATTACK\":31,\"DEFENSE\":31,\"SPECIAL_ATTACK\":31,\"SPECIAL_DEFENSE\":31,\"SPEED\":31},"
                + "\"evs\":{\"HP\":0,\"ATTACK\":0,\"DEFENSE\":0,\"SPECIAL_ATTACK\":0,\"SPECIAL_DEFENSE\":0,\"SPEED\":0}"
                + "}";

        webClient.post().uri("/api/v1/trainers/ash/pokemon")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.instanceId").isEqualTo(id.toString())
                .jsonPath("$.location").isEqualTo("PARTY");
    }
}
