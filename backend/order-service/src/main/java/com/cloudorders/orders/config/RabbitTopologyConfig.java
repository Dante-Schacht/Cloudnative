package com.cloudorders.orders.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans Exchange, Queue y Binding de la topología de pedidos; RabbitAdmin los declara al abrir la
 * primera conexión. Colas quorum: replicadas entre los nodos del clúster. Los nombres vienen de
 * application.yml (ver RabbitTopologyProperties).
 *
 * <p>Rutas de mensajería (caso de uso -> binding en el exchange topic -> cola -> DLQ vía exchange direct):
 * <ul>
 *   <li>Notificación al cliente: {@code orders.created} -> cola created -> DLQ created.</li>
 *   <li>Auditoría de todos los eventos de pedidos: {@code orders.#} -> cola audit -> DLQ audit.</li>
 *   <li>Reserva de inventario: {@code orders.created} -> cola inventory -> DLQ inventory.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(RabbitTopologyProperties.class)
public class RabbitTopologyConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitTopologyConfig.class);

    @Bean
    public TopicExchange ordersExchange(RabbitTopologyProperties props) {
        return ExchangeBuilder.topicExchange(props.exchange()).durable(true).build();
    }

    @Bean
    public DirectExchange ordersDeadLetterExchange(RabbitTopologyProperties props) {
        return ExchangeBuilder.directExchange(props.deadLetterExchange()).durable(true).build();
    }

    @Bean
    public Queue ordersCreatedQueue(RabbitTopologyProperties props) {
        return mainQueue(props, props.queues().created());
    }

    @Bean
    public Queue ordersCreatedDeadLetterQueue(RabbitTopologyProperties props) {
        return deadLetterQueue(props.queues().created());
    }

    @Bean
    public Binding ordersCreatedBinding(RabbitTopologyProperties props) {
        return mainBinding(ordersCreatedQueue(props), ordersExchange(props), props.queues().created());
    }

    @Bean
    public Binding ordersCreatedDeadLetterBinding(RabbitTopologyProperties props) {
        return deadLetterBinding(ordersCreatedDeadLetterQueue(props), ordersDeadLetterExchange(props),
                props.queues().created());
    }

    @Bean
    public Queue ordersAuditQueue(RabbitTopologyProperties props) {
        return mainQueue(props, props.queues().audit());
    }

    @Bean
    public Queue ordersAuditDeadLetterQueue(RabbitTopologyProperties props) {
        return deadLetterQueue(props.queues().audit());
    }

    @Bean
    public Binding ordersAuditBinding(RabbitTopologyProperties props) {
        return mainBinding(ordersAuditQueue(props), ordersExchange(props), props.queues().audit());
    }

    @Bean
    public Binding ordersAuditDeadLetterBinding(RabbitTopologyProperties props) {
        return deadLetterBinding(ordersAuditDeadLetterQueue(props), ordersDeadLetterExchange(props),
                props.queues().audit());
    }

    @Bean
    public Queue ordersInventoryQueue(RabbitTopologyProperties props) {
        return mainQueue(props, props.queues().inventory());
    }

    @Bean
    public Queue ordersInventoryDeadLetterQueue(RabbitTopologyProperties props) {
        return deadLetterQueue(props.queues().inventory());
    }

    @Bean
    public Binding ordersInventoryBinding(RabbitTopologyProperties props) {
        return mainBinding(ordersInventoryQueue(props), ordersExchange(props), props.queues().inventory());
    }

    @Bean
    public Binding ordersInventoryDeadLetterBinding(RabbitTopologyProperties props) {
        return deadLetterBinding(ordersInventoryDeadLetterQueue(props), ordersDeadLetterExchange(props),
                props.queues().inventory());
    }

    private static Queue mainQueue(RabbitTopologyProperties props, RabbitTopologyProperties.QueueSpec spec) {
        return QueueBuilder.durable(spec.name())
                .quorum()
                .deadLetterExchange(props.deadLetterExchange())
                .deadLetterRoutingKey(spec.deadLetterRoutingKey())
                .build();
    }

    private static Queue deadLetterQueue(RabbitTopologyProperties.QueueSpec spec) {
        return QueueBuilder.durable(spec.deadLetterQueue()).quorum().build();
    }

    private static Binding mainBinding(Queue queue, TopicExchange exchange,
            RabbitTopologyProperties.QueueSpec spec) {
        return BindingBuilder.bind(queue).to(exchange).with(spec.bindingKey());
    }

    private static Binding deadLetterBinding(Queue queue, DirectExchange exchange,
            RabbitTopologyProperties.QueueSpec spec) {
        return BindingBuilder.bind(queue).to(exchange).with(spec.deadLetterRoutingKey());
    }

    @Bean
    @ConditionalOnProperty(name = "cloudorders.rabbitmq.declare-on-startup", matchIfMissing = true)
    public ApplicationRunner topologyInitializer(AmqpAdmin amqpAdmin) {
        return args -> {
            try {
                amqpAdmin.initialize();
            } catch (AmqpException ex) {
                // RabbitAdmin repite la declaración al reconectar; no se impide el arranque del servicio.
                log.warn("RabbitMQ no disponible al iniciar; la topología se declarará al conectar: {}",
                        ex.getMessage());
            }
        };
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
