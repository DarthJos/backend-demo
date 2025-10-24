package com.jr.distributed_inventory_system.controller;

import com.jr.distributed_inventory_system.exception.StockNotAvailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para mapear errores a respuestas HTTP.
 * Hereda de ResponseEntityExceptionHandler para manejar excepciones comunes de Spring MVC.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Método de utilidad para crear un cuerpo de respuesta de error estandarizado
    private Map<String, Object> createErrorBody(HttpStatus status, String message, String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new java.util.Date());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return body;
    }

    // -------------------------------------------------------------------------
    // 1. MANEJO DE ERRORES DE NEGOCIO (409 CONFLICT)
    // -------------------------------------------------------------------------

    /**
     * Mapea StockNotAvailableException a HTTP 409 Conflict.
     * Esto ocurre cuando no hay suficiente stock.
     */
    @ExceptionHandler(StockNotAvailableException.class)
    public ResponseEntity<Object> handleStockNotAvailable(StockNotAvailableException ex) {
        return new ResponseEntity<>(
                createErrorBody(HttpStatus.CONFLICT, ex.getMessage(), "Conflict"),
                HttpStatus.CONFLICT // 409
        );
    }

    // -------------------------------------------------------------------------
    // 2. MANEJO DE ERRORES HTTP COMUNES (4XX y 5XX)
    // -------------------------------------------------------------------------

    /**
     * Maneja ResponseStatusException (usada para 404 Not Found cuando el Optional está vacío).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        return new ResponseEntity<>(
                createErrorBody((HttpStatus) ex.getStatusCode(), ex.getReason(), ex.getStatusCode().toString()),
                ex.getStatusCode()
        );
    }

    /**
     * Maneja cualquier error de tipo RuntimeException no capturado, como fallos en el servicio de pago
     * que no tienen un fallback.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleAllRuntimeExceptions(RuntimeException ex) {
        // En producción, solo retornaríamos un mensaje genérico por seguridad
        return new ResponseEntity<>(
                createErrorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor.", "Internal Server Error"),
                HttpStatus.INTERNAL_SERVER_ERROR // 500
        );
    }

    // -------------------------------------------------------------------------
    // 3. MANEJO DE ERRORES LANZADOS POR SPRING MVC (400 BAD REQUEST, etc.)
    // -------------------------------------------------------------------------

    /**
     * Sobrescribe el método handleMethodArgumentNotValid para manejar errores de validación.
     * Mapea a 400 Bad Request.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String defaultMessage = ex.getBindingResult().getFieldError() != null ?
                ex.getBindingResult().getFieldError().getDefaultMessage() :
                "Argumentos de método no válidos.";

        return new ResponseEntity<>(
                createErrorBody(HttpStatus.BAD_REQUEST, defaultMessage, "Bad Request"),
                HttpStatus.BAD_REQUEST // 400
        );
    }

    /**
     * Sobrescribe el manejo de otras excepciones del framework, como
     * MissingServletRequestParameterException (parámetro GET faltante) o
     * HttpMessageNotReadableException (cuerpo JSON inválido).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        HttpStatus httpStatus = (HttpStatus) status;
        String errorType = httpStatus.is4xxClientError() ? "Client Error" : "Server Error";

        return new ResponseEntity<>(
                createErrorBody(httpStatus, ex.getMessage(), errorType),
                status
        );
    }
}