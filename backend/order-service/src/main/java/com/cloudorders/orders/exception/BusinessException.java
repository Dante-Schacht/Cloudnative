package com.cloudorders.orders.exception;

/** Error controlado de negocio; se responde como HTTP 422. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
