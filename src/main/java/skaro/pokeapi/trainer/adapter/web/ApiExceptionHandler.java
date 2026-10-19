package skaro.pokeapi.trainer.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import skaro.pokeapi.trainer.application.port.OptimisticLockException;
import skaro.pokeapi.trainer.domain.exception.BoxFullException;
import skaro.pokeapi.trainer.domain.exception.InstanceNotFoundException;
import skaro.pokeapi.trainer.domain.exception.InvalidAbilityException;
import skaro.pokeapi.trainer.domain.exception.InvalidEffortValuesException;
import skaro.pokeapi.trainer.domain.exception.InvalidEvolutionException;
import skaro.pokeapi.trainer.domain.exception.InvalidMovesetException;
import skaro.pokeapi.trainer.domain.exception.InvalidStatValueException;
import skaro.pokeapi.trainer.domain.exception.PartyFullException;
import skaro.pokeapi.trainer.domain.exception.SpeciesNotFoundException;
import skaro.pokeapi.trainer.domain.exception.StorageFullException;
import skaro.pokeapi.trainer.domain.exception.TrainerNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InstanceNotFoundException.class, TrainerNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({PartyFullException.class, BoxFullException.class, StorageFullException.class,
            OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({SpeciesNotFoundException.class, InvalidAbilityException.class,
            InvalidEvolutionException.class, InvalidStatValueException.class,
            InvalidEffortValuesException.class, InvalidMovesetException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> unprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("title", status.getReasonPhrase());
        body.put("detail", detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
