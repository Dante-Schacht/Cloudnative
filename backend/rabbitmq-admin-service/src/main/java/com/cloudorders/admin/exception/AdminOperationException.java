package com.cloudorders.admin.exception;

import org.springframework.http.HttpStatus;

/** Error controlado de la API de administración, con el estado HTTP que debe devolverse. */
public class AdminOperationException extends RuntimeException {

    private final HttpStatus status;

    public AdminOperationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
