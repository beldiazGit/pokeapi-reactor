package skaro.pokeapi.trainer.adapter.pokeapi;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import skaro.pokeapi.client.MapEndpointRegistry;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.client.PokeApiEndpointRegistry;
import skaro.pokeapi.client.ReactiveNonCachingPokeApiClient;
import skaro.pokeapi.client.WebClientEntityFactory;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.trainer.domain.Stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokeApiCatalogAdapterTest {

    private MockWebServer server;
    private PokeApiClient client;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        // PokéAPI JSON uses snake_case (e.g. base_stat, front_default); the production
        // WebClient (PokeApiReactorBaseConfiguration) wires a Jackson2JsonDecoder with
        // SNAKE_CASE naming so those fields map onto camelCase Java properties. Reproduce
        // that here, otherwise fields like PokemonStat.baseStat deserialize as null.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().jackson2JsonDecoder(decoder))
                .build();

        webClient = WebClient.builder()
                .baseUrl(String.format("http://localhost:%s", server.getPort()))
                .exchangeStrategies(strategies)
                .build();
        PokeApiEndpointRegistry registry = new MapEndpointRegistry(Map.of(Pokemon.class, "/pokemon"));
        WebClientEntityFactory entityFactory = new WebClientEntityFactory(webClient, registry);
        client = new ReactiveNonCachingPokeApiClient(entityFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void mapsSpeciesAbilitiesTypesAndStats() {
        String body = "{"
                + "\"name\":\"bulbasaur\","
                + "\"abilities\":[{\"ability\":{\"name\":\"overgrow\",\"url\":\"x\"},\"is_hidden\":false,\"slot\":1}],"
                + "\"types\":[{\"slot\":1,\"type\":{\"name\":\"grass\",\"url\":\"x\"}}],"
                + "\"stats\":[{\"base_stat\":45,\"effort\":0,\"stat\":{\"name\":\"hp\",\"url\":\"x\"}}],"
                + "\"sprites\":{\"front_default\":\"http://img/1.png\"}"
                + "}";
        server.enqueue(new MockResponse().setBody(body)
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

        PokeApiCatalogAdapter adapter = new PokeApiCatalogAdapter(client, webClient);

        StepVerifier.create(adapter.getSpecies("bulbasaur"))
                .assertNext(data -> {
                    assertEquals("bulbasaur", data.getName());
                    assertTrue(data.getAbilities().contains("overgrow"));
                    assertTrue(data.getTypes().contains("grass"));
                    assertEquals(45, data.getBaseStats().get(Stat.HP));
                    assertEquals("http://img/1.png", data.getSpriteUrl());
                })
                .verifyComplete();
    }
}
