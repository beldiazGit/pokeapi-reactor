package skaro.pokeapi.trainer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger definition for the Trainer Pokémon Box & Active Party API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trainerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trainer — Pokémon Box & Active Party API")
                        .version("1.0")
                        .description("Manage a trainer's individual Pokémon: capture, active party vs storage box, "
                                + "transfers and evolution. Built on the pokeapi-reactor library."));
    }
}
