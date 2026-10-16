package skaro.pokeapi.trainer.adapter.pokeapi;

import java.util.HashMap;
import java.util.List;
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

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import skaro.pokeapi.client.MapEndpointRegistry;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.client.PokeApiEndpointRegistry;
import skaro.pokeapi.client.ReactiveNonCachingPokeApiClient;
import skaro.pokeapi.client.WebClientEntityFactory;
import skaro.pokeapi.resource.pokemon.Pokemon;
import skaro.pokeapi.resource.pokemonspecies.PokemonSpecies;
import skaro.pokeapi.trainer.application.port.EvolutionOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PokeApiCatalogAdapter#directEvolutionsOf(String)} end-to-end against a
 * {@link MockWebServer}, driving the REAL chain-walking logic (species fetch -> evolution
 * chain fetch-by-URL -> direct-children extraction -> per-target ability lookup). The existing
 * {@code EvolveUseCaseTest} only exercises a fake catalog port, so that path had no coverage.
 */
class PokeApiCatalogAdapterEvolutionTest {

    private MockWebServer server;
    private PokeApiClient client;
    private WebClient webClient;
    private Map<String, String> responseBodies;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        responseBodies = new HashMap<>();

        // Route by request path (not sequential enqueue): directEvolutionsOf issues several
        // calls (species, chain, target pokemon) whose relative order is an implementation
        // detail we don't want to hard-code into the test.
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                String body = responseBodies.get(path);
                if (body == null) {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setBody(body)
                        .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            }
        });
        server.start();

        // PokéAPI JSON uses snake_case; reproduce the production decoder configuration
        // (see PokeApiCatalogAdapterTest) so fields like evolution_chain/evolves_to map
        // onto the camelCase Java properties.
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
        PokeApiEndpointRegistry registry = new MapEndpointRegistry(Map.of(
                Pokemon.class, "/pokemon",
                PokemonSpecies.class, "/pokemon-species"));
        WebClientEntityFactory entityFactory = new WebClientEntityFactory(webClient, registry);
        client = new ReactiveNonCachingPokeApiClient(entityFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private String pokemonJson(String name, String ability) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"abilities\":[{\"ability\":{\"name\":\"" + ability + "\",\"url\":\"x\"},\"is_hidden\":false,\"slot\":1}],"
                + "\"types\":[{\"slot\":1,\"type\":{\"name\":\"water\",\"url\":\"x\"}}],"
                + "\"stats\":[{\"base_stat\":50,\"effort\":0,\"stat\":{\"name\":\"hp\",\"url\":\"x\"}}],"
                + "\"sprites\":{\"front_default\":\"http://img/" + name + ".png\"}"
                + "}";
    }

    private String speciesJson(String name, String evolutionChainUrl) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"evolution_chain\":{\"url\":\"" + evolutionChainUrl + "\"}"
                + "}";
    }

    @Test
    void returnsTheSingleDirectEvolutionForALinearChain() {
        String chainUrl = server.url("/evolution-chain/1").toString();
        responseBodies.put("/pokemon-species/magikarp", speciesJson("magikarp", chainUrl));
        responseBodies.put("/evolution-chain/1", "{"
                + "\"id\":1,"
                + "\"chain\":{"
                + "  \"species\":{\"name\":\"magikarp\",\"url\":\"x\"},"
                + "  \"evolves_to\":[{"
                + "    \"species\":{\"name\":\"gyarados\",\"url\":\"x\"},"
                + "    \"evolves_to\":[]"
                + "  }]"
                + "}}");
        responseBodies.put("/pokemon/gyarados", pokemonJson("gyarados", "intimidate"));

        PokeApiCatalogAdapter adapter = new PokeApiCatalogAdapter(client, webClient);

        StepVerifier.create(adapter.directEvolutionsOf("magikarp"))
                .assertNext(option -> {
                    assertEquals("magikarp", option.getFromSpecies());
                    assertEquals("gyarados", option.getToSpecies());
                    assertFalse(option.getAllowedAbilities().isEmpty());
                    assertTrue(option.getAllowedAbilities().contains("intimidate"));
                })
                .verifyComplete();
    }

    @Test
    void returnsBothDirectChildrenForABranchingChain() {
        String chainUrl = server.url("/evolution-chain/2").toString();
        responseBodies.put("/pokemon-species/eevee", speciesJson("eevee", chainUrl));
        responseBodies.put("/evolution-chain/2", "{"
                + "\"id\":2,"
                + "\"chain\":{"
                + "  \"species\":{\"name\":\"eevee\",\"url\":\"x\"},"
                + "  \"evolves_to\":[{"
                + "    \"species\":{\"name\":\"vaporeon\",\"url\":\"x\"},"
                + "    \"evolves_to\":[]"
                + "  },{"
                + "    \"species\":{\"name\":\"jolteon\",\"url\":\"x\"},"
                + "    \"evolves_to\":[]"
                + "  }]"
                + "}}");
        responseBodies.put("/pokemon/vaporeon", pokemonJson("vaporeon", "water-absorb"));
        responseBodies.put("/pokemon/jolteon", pokemonJson("jolteon", "volt-absorb"));

        PokeApiCatalogAdapter adapter = new PokeApiCatalogAdapter(client, webClient);

        List<String> targets = adapter.directEvolutionsOf("eevee")
                .map(EvolutionOption::getToSpecies)
                .collectList()
                .block();

        assertEquals(2, targets.size());
        assertTrue(targets.contains("vaporeon"));
        assertTrue(targets.contains("jolteon"));
    }
}
