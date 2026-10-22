package com.cloudorders.admin.service;

import com.cloudorders.admin.config.AdminProperties;
import com.cloudorders.admin.dto.BindingRequest;
import com.cloudorders.admin.dto.CreateExchangeRequest;
import com.cloudorders.admin.dto.CreateQueueRequest;
import com.cloudorders.admin.dto.ExchangeResponse;
import com.cloudorders.admin.dto.QueueResponse;
import com.cloudorders.admin.dto.QueueStatsResponse;
import com.cloudorders.admin.dto.QueueType;
import com.cloudorders.admin.dto.ResourceNames;
import com.cloudorders.admin.exception.AdminOperationException;
import com.cloudorders.admin.exception.AmqpErrors;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Operaciones de administración sobre RabbitMQ. Solo actúa sobre recursos cuyo nombre empieza con el
 * prefijo gestionado, por lo que no puede alterar la topología en uso (orders.*).
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final AdminProperties properties;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, AdminProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public QueueResponse createQueue(CreateQueueRequest request) {
        requireManaged("cola", request.name());
        QueueBuilder builder = request.durableOrDefault()
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());
        if (request.typeOrDefault() == QueueType.QUORUM) {
            builder.quorum();
        }
        Queue queue = builder.build();
        amqpAdmin.declareQueue(queue);
        log.info("Cola creada: {} (type={}, durable={})", request.name(), request.typeOrDefault(),
                request.durableOrDefault());
        return new QueueResponse(request.name(), request.durableOrDefault(), request.typeOrDefault());
    }

    public QueueStatsResponse getQueue(String name) {
        requireManaged("cola", name);
        return queueStats(name);
    }

    /**
     * Elimina la cola solo si no tiene consumidores ni mensajes. La comprobación es explícita porque las
     * colas quorum no admiten los flags if-unused/if-empty de queue.delete.
     */
    public void deleteQueue(String name) {
        requireManaged("cola", name);
        QueueStatsResponse stats = queueStats(name);
        if (stats.messageCount() > 0 || stats.consumerCount() > 0) {
            throw new AdminOperationException(HttpStatus.CONFLICT, "La cola '" + name + "' tiene "
                    + stats.messageCount() + " mensaje(s) y " + stats.consumerCount() + " consumidor(es); "
                    + "solo se pueden eliminar colas vacías y sin consumidores.");
        }
        amqpAdmin.deleteQueue(name);
        log.info("Cola eliminada: {}", name);
    }

    private QueueStatsResponse queueStats(String name) {
        Properties stats = amqpAdmin.getQueueProperties(name);
        if (stats == null) {
            throw notFound("cola", name);
        }
        return new QueueStatsResponse(name,
                ((Number) stats.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue(),
                ((Number) stats.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue());
    }

    public ExchangeResponse createExchange(CreateExchangeRequest request) {
        requireManaged("exchange", request.name());
        boolean durable = request.durableOrDefault();
        Exchange exchange = switch (request.type()) {
            case DIRECT -> new DirectExchange(request.name(), durable, false);
            case TOPIC -> new TopicExchange(request.name(), durable, false);
            case FANOUT -> new FanoutExchange(request.name(), durable, false);
            case HEADERS -> new HeadersExchange(request.name(), durable, false);
        };
        amqpAdmin.declareExchange(exchange);
        log.info("Exchange creado: {} (type={}, durable={})", request.name(), request.type(), durable);
        return new ExchangeResponse(request.name(), request.type(), durable);
    }

    /** Elimina el exchange solo si no tiene bindings. */
    public void deleteExchange(String name) {
        requireManaged("exchange", name);
        if (!exchangeExists(name)) {
            throw notFound("exchange", name);
        }
        rabbitTemplate.execute(channel -> {
            channel.exchangeDelete(name, true);
            return null;
        });
        log.info("Exchange eliminado: {}", name);
    }

    public BindingRequest createBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        amqpAdmin.declareBinding(binding);
        log.info("Binding creado: {} -> {} ({})", request.exchange(), request.queue(), request.routingKey());
        return request;
    }

    public void deleteBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        amqpAdmin.removeBinding(binding);
        log.info("Binding eliminado: {} -> {} ({})", request.exchange(), request.queue(), request.routingKey());
    }

    private Binding toBinding(BindingRequest request) {
        requireManaged("exchange", request.exchange());
        requireManaged("cola", request.queue());
        return new Binding(request.queue(), Binding.DestinationType.QUEUE, request.exchange(),
                request.routingKey(), null);
    }

    private boolean exchangeExists(String name) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(name));
            return true;
        } catch (AmqpException ex) {
            if (AmqpErrors.isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private void requireManaged(String kind, String name) {
        if (name == null || !name.matches(ResourceNames.NAME_PATTERN)) {
            throw new AdminOperationException(HttpStatus.BAD_REQUEST, "Nombre de " + kind + " inválido.");
        }
        if (!name.startsWith(properties.managedPrefix())) {
            throw new AdminOperationException(HttpStatus.FORBIDDEN, "Solo se pueden administrar recursos cuyo nombre "
                    + "empiece con '" + properties.managedPrefix() + "'; '" + name + "' está protegido.");
        }
    }

    private AdminOperationException notFound(String kind, String name) {
        return new AdminOperationException(HttpStatus.NOT_FOUND, "No existe la " + kind + " '" + name + "'.");
    }
}
