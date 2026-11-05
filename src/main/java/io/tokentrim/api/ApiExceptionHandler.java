package io.tokentrim.api;

import io.tokentrim.upstream.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<String> upstream(UpstreamException exception) {
        String body = exception.body();
        if (body == null || body.isBlank()) {
            body = Errors.json(exception.getMessage(), "upstream_error");
        }
        if (exception.status() >= 500) {
            log.warn("Upstream HTTP {}: {}", exception.status(), abbreviate(body));
        } else {
            log.info("Upstream HTTP {}: {}", exception.status(), abbreviate(body));
        }
        return ResponseEntity.status(exception.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Errors.of("Request body is not valid JSON", "invalid_request_error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> fallback(Exception exception) throws Exception {
        if (exception instanceof NoResourceFoundException || exception instanceof ResponseStatusException) {
            throw exception;
        }
        log.error("Unhandled proxy error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Errors.of("TokenTrim failed while handling the request", "internal_error"));
    }

    private static String abbreviate(String body) {
        String flat = body.replace('\n', ' ');
        return flat.length() <= 300 ? flat : flat.substring(0, 300);
    }
}
