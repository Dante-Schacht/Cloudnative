package com.cloudorders.notifications.processing;

/** Mensaje inválido o fallo que no se resuelve reintentando: va directo a la DLQ. */
public class NonRecoverableEventException extends RuntimeException {

    public NonRecoverableEventException(String message) {
        super(message);
    }

    public NonRecoverableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
