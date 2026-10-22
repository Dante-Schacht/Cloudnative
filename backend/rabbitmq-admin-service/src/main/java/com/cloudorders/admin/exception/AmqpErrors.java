package com.cloudorders.admin.exception;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import java.util.Optional;
import org.springframework.amqp.AmqpException;

/** Extrae el código de cierre de canal (404, 406, ...) que RabbitMQ devuelve ante un error de recurso. */
public final class AmqpErrors {

    private AmqpErrors() {
    }

    public static Optional<Integer> channelCloseCode(AmqpException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal && signal.getReason() instanceof AMQP.Channel.Close close) {
                return Optional.of(close.getReplyCode());
            }
        }
        return Optional.empty();
    }

    public static boolean isNotFound(AmqpException exception) {
        return channelCloseCode(exception).filter(code -> code == 404).isPresent();
    }
}
