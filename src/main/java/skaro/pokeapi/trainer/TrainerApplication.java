package skaro.pokeapi.trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrainerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainerApplication.class, args);
    }
}
