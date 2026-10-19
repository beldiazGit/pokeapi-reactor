[![CD Pipeline](https://github.com/SirSkaro/pokeapi-reactor/actions/workflows/maven-publish.yml/badge.svg?branch=v1.0.2)](https://github.com/SirSkaro/pokeapi-reactor/actions/workflows/maven-publish.yml)
![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

# pokeapi-reactor
A non-blocking, reactive API client for [PokeAPI](https://pokeapi.co/) with caching for Spring Boot projects.

---

## Domain: Pokémon Box and Active Party

This fork extends the `pokeapi-reactor` library with a domain application that manages
a trainer's individual Pokémon (Active Party vs. Box). See the design document at
[`docs/superpowers/specs/2026-10-15-baul-equipo-pokemon-design.md`](docs/superpowers/specs/2026-10-15-baul-equipo-pokemon-design.md).

### Architecture
Hexagonal, in a single module under `skaro.pokeapi.trainer`: `domain` (pure) ·
`application` (reactive use cases) · `adapter/{web,persistence,pokeapi}`. Persistence
is in-memory behind a port (`PokemonRepository`); PokéAPI is consumed via
`PokemonCatalogPort` on top of the existing `PokeApiClient`.

### How to run
```bash
./mvnw spring-boot:run
# Embedded Netty on http://localhost:8080
```

Configurable limits in `application.yml` (`trainer.party.max-size`,
`trainer.box.max-size`).

### Endpoints
| Method + path | What it does |
|---|---|
| `POST /api/v1/trainers/{id}/pokemon` | Capture/create (auto party→box), `201` |
| `GET /api/v1/trainers/{id}/pokemon/{uuid}` | Specimen detail |
| `GET /api/v1/trainers/{id}/party` | Active party (composite view with PokéAPI) |
| `GET /api/v1/trainers/{id}/box` | Box |
| `PATCH /api/v1/trainers/{id}/pokemon/{uuid}` | Transfer `{"location":"PARTY"\|"BOX"}` |
| `POST /api/v1/trainers/{id}/pokemon/{uuid}/evolve` | Evolution (bonus) |

### Example
```bash
curl -X POST http://localhost:8080/api/v1/trainers/ash/pokemon \
  -H 'Content-Type: application/json' \
  -d '{"species":"bulbasaur","nature":"hardy","ability":"overgrow","gender":"MALE",
       "initialLevel":5,"originalTrainerId":"ash","pokeball":"poke-ball",
       "moves":["tackle","growl"],
       "ivs":{"HP":31,"ATTACK":31,"DEFENSE":31,"SPECIAL_ATTACK":31,"SPECIAL_DEFENSE":31,"SPEED":31},
       "evs":{"HP":0,"ATTACK":0,"DEFENSE":0,"SPECIAL_ATTACK":0,"SPECIAL_DEFENSE":0,"SPEED":0}}'
```

### Known limitations
Following a Pokémon's evolution chain bypasses the library's cache: the evolution
chain is referenced as an unnamed `ApiResource` (URL only) and cannot be routed
through `PokeApiClient`/`CacheFacade`. As a result, each `evolve` call re-fetches the
evolution chain uncached — a known, deliberate limitation.

---

### Features
* Simple, single [entry point](../master/src/main/java/skaro/pokeapi/client/PokeApiClient.java) for all client operations.
* Non-blocking HTTP operations and non-blocking caching.
* Fully customizable caching. Supports Spring Boot's generic [CacheManager](https://docs.spring.io/spring-boot/docs/1.3.0.M1/reference/html/boot-features-caching.html#_supported_cache_providers) for caching.
* Caching and non-caching configurations.

## Getting started
### Project Configuration

#### Properties
You can (must) configure the location of the PokeAPI instance you want to use. Add the following property to your `application.properties`:

```
skaro.pokeapi.base-uri=https://pokeapi.co/api/v2/ #or the url of your own instance
```

You may also configure the max buffer size for the WebClient, which is used to fetch resources from PokeAPI.
Its default value is 565000 bytes, while the API request for "/pokemon/mew" can grow over the time, you may want to increase it yourself to a higher value.
To achieve this, add the following property to your `application.properties`:
```
skaro.pokeapi.max-buffer-size=565000
```

#### Application Context
Import one of pokeapi-reactor's configurations as well as specify your own [reactor.netty.http.client.HttpClient](https://projectreactor.io/docs/netty/release/api/reactor/netty/http/client/HttpClient.html) bean. Two configurations are available: caching and non-caching. Below is an example of a caching configuration which uses a flexible `HttpClient` tuned for high parallel throughput.

```java
@Configuration
@Import(PokeApiReactorCachingConfiguration.class)
@EnableCaching
public class MyPokeApiReactorCachingConfiguration {
	@Bean
	public ConnectionProvider connectionProvider() {
	    return ConnectionProvider.builder("Auto refresh & no connection limit")
		    .maxIdleTime(Duration.ofSeconds(10))
		    .maxConnections(500)
		    .pendingAcquireMaxCount(-1)
		    .build();
	}

	@Bean
	public HttpClient httpClient(ConnectionProvider connectionProvider) {
		return HttpClient.create(connectionProvider)
                  .compress(true)
                  .resolver(DefaultAddressResolverGroup.INSTANCE);
	}
}
```
Or, if you'd rather not enable caching:
```java
@Configuration
@Import(PokeApiReactorNonCachingConfiguration.class)
public class MyPokeApiReactorNonCachingConfiguration {
	@Bean
	public ConnectionProvider connectionProvider() { ... }
	
	@Bean
	public HttpClient httpClient(ConnectionProvider connectionProvider) { ... }
}
```
Both the `PokeApiReactorCachingConfiguration` and `PokeApiReactorNonCachingConfiguration` will register the appropriate `PokeApiClient` bean.

### Fetching a resource
Inject the registered `PokeApiClient` into your class and request a resource.
```java
@Autowired
private PokeApiClient pokeApiClient;

...

public void printPokemon() {
   pokeApiClient.getResource(Pokemon.class, "pikachu")
      .map(pokemon -> String.format("%s has %d forms", pokemon.getName(), pokemon.getForms().size()))
      .subscribe(System.out::println);
}
```
If you don't mind blocking, you can simply block for the resource.
```java
public void printPokemon() {
   Pokemon pokemon = pokeApiClient.getResource(Pokemon.class, "pikachu").block();
   String pokemonInfo = String.format("%s has %d forms", pokemon.getName(), pokemon.getForms().size()));
   System.out.println(pokemonInfo);
}
```

### Following a resource
Following links to other resources also has first-class support.

```java
public void printPokemonForms() {
   pokeApiClient.getResource(Pokemon.class, "pikachu")
      .flatMapMany(pokemon -> pokeApiClient.followResources(pokemon::getForms, PokemonForm.class))
      .map(form -> String.format("Pikachu has a form called %s", form.getName()))
      .subscribe(System.out::println);
}
```
Again, with blocking:
```java
public void printPokemonForms() {
   Pokemon pokemon = pokeApiClient.getResource(Pokemon.class, "pikachu").block();
   List<PokemonForm> forms = pokeApiClient.followResources(pokemon::getForms, PokemonForm.class)
      .collectList()
      .block();

   for(PokemonForm form : forms) {
      String formInfo = String.format("Pikachu has a form called %s", form.getName());
      System.out.println(formInfo);
   }
}
```

### Maven Configuration
The latest release can be pulled from GitHub's Apache Maven repository. To pull from their repository, you must add your GitHub credentials to your [settings.xml](https://maven.apache.org/settings.html) (located in `${user.home}/.m2/settings.xml`). You can read [GitHub's article about how to do that](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry). But for impatient programmer's, below is yet another guide to configure Maven to pull from GitHub that holds your hand a little more closely - I always appreciate it when people do that.


#### Generate a token
[Generate a PAT](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) (Personal Access Token) with the `read:packages` privilege.

#### Add \<server\> and \<repository\> tags in settings.xml 
Configure Maven to [authenticate when trying to pull from GitHub's Apache Maven repository](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-with-a-personal-access-token). If you don't have a settings.xml, create it under your .m2 directory. It should look something like this.

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>

  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo1.maven.org/maven2</url>
        </repository>
        <repository>
          <id>github-skaro-pokeapi-reactor</id>
          <url>https://maven.pkg.github.com/SirSkaro/pokeapi-reactor</url>
        </repository>
      </repositories>
    </profile>
  </profiles>

  <servers>
    <server>
      <id>github-skaro-pokeapi-reactor</id>
      <username>YOUR USERNAME</username>
      <password>YOUR TOKEN YOU JUST CREATED</password>
    </server>
  </servers>
</settings>
```



