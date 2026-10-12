package gamelife.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns a GameException into a JSON answer {"error": "..."} with the right HTTP status. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(GameException.class)
    public ResponseEntity<Map<String, String>> handle(GameException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}
