package org.example.sistema_gestion_vitalexa.exception;

import org.example.sistema_gestion_vitalexa.exceptions.BusinessExeption;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessExeption.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessExeption ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        response.put("path", ""); // Path is harder to get here without request object, but generic is fine

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
