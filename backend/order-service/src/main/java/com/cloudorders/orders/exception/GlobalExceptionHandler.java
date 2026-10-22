package com.cloudorders.orders.exception;

import com.cloudorders.orders.dto.ApiError;
import com.cloudorders.orders.messaging.EventPublicationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return build(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o contiene tipos de datos incorrectos.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' tiene un tipo incorrecto.",
                List.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of());
    }

    @ExceptionHandler(EventPublicationException.class)
    public ResponseEntity<ApiError> handleEventPublication(EventPublicationException ex) {
        log.error("No se pudo confirmar el evento del pedido; el pedido no fue registrado", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo confirmar la publicación del evento del pedido, por lo que el pedido no fue registrado. "
                        + "Inténtalo nuevamente más tarde.",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return build(status, "No se pudo procesar la solicitud.", List.of());
        }
        log.error("Error interno no controlado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Inténtalo nuevamente más tarde.",
                List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message, List<String> details) {
        String reason = status instanceof HttpStatus httpStatus ? httpStatus.getReasonPhrase() : status.toString();
        ApiError body = new ApiError(OffsetDateTime.now(clock), status.value(), reason, message, details);
        return ResponseEntity.status(status).body(body);
    }
}
