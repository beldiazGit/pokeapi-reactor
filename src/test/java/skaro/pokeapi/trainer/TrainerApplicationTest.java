package skaro.pokeapi.trainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import skaro.pokeapi.client.PokeApiClient;
import skaro.pokeapi.trainer.config.StorageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TrainerApplicationTest {

    @Autowired
    private PokeApiClient pokeApiClient;

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void contextLoadsWithPokeApiClientAndStorageDefaults() {
        assertNotNull(pokeApiClient);
        assertEquals(6, storageProperties.getParty().getMaxSize());
        assertEquals(30, storageProperties.getBox().getMaxSize());
    }
}
