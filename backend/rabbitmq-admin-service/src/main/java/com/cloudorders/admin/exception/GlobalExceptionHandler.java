package com.cloudorders.admin.exception;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o contiene valores no permitidos.");
    }

    @ExceptionHandler(AdminOperationException.class)
    public ProblemDetail handleOperation(AdminOperationException ex) {
        return ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(AmqpException.class)
    public ProblemDetail handleAmqp(AmqpException ex) {
        int code = AmqpErrors.channelCloseCode(ex).orElse(0);
        if (code == 404) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                    "Un recurso referenciado (exchange o cola) no existe.");
        }
        if (code == 405 || code == 406) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "El recurso existe con otra configuración, está en uso o no está vacío.");
        }
        if (ex instanceof AmqpConnectException) {
            log.error("RabbitMQ no disponible", ex);
            return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con RabbitMQ.");
        }
        log.error("Error de RabbitMQ", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "RabbitMQ rechazó la operación.");
    }
}
