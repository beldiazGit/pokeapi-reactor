package skaro.pokeapi.trainer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trainer")
public class StorageProperties {

    private final Limit party = new Limit();
    private final Limit box = new Limit();

    public Limit getParty() { return party; }
    public Limit getBox() { return box; }

    public static class Limit {
        private int maxSize;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }
}
