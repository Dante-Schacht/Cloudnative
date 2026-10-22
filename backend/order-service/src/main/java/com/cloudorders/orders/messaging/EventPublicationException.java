package com.cloudorders.orders.messaging;

/** El evento del pedido no pudo publicarse o el broker no lo confirmó. */
public class EventPublicationException extends RuntimeException {

    public EventPublicationException(String message) {
        super(message);
    }

    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
