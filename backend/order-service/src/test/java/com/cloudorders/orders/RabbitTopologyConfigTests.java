package com.cloudorders.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RabbitTopologyConfigTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void declaresDirectAndTopicExchangesFromConfiguration() {
        Map<String, Exchange> exchanges = context.getBeansOfType(Exchange.class);

        assertThat(exchanges.values()).hasSize(2).allSatisfy(exchange -> assertThat(exchange.isDurable()).isTrue());
        assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("orders.exchange");
        assertThat(context.getBean(DirectExchange.class).getName()).isEqualTo("orders.dlx");
    }

    @Test
    void declaresThreeMainQueuesAndThreeDeadLetterQueuesAsQuorum() {
        List<Queue> queues = List.copyOf(context.getBeansOfType(Queue.class).values());

        assertThat(queues).hasSize(6).allSatisfy(queue -> {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments()).containsEntry("x-queue-type", "quorum");
        });
        assertThat(queues).extracting(Queue::getName).containsExactlyInAnyOrder(
                "orders.created.queue", "orders.audit.queue", "orders.inventory.queue",
                "orders.created.dlq", "orders.audit.dlq", "orders.inventory.dlq");
        assertThat(queues).filteredOn(queue -> queue.getName().endsWith(".queue")).hasSize(3)
                .allSatisfy(queue -> assertThat(queue.getArguments())
                        .containsEntry("x-dead-letter-exchange", "orders.dlx")
                        .containsKey("x-dead-letter-routing-key"));
    }

    @Test
    void bindsQueuesWithConfiguredRoutingKeys() {
        List<Binding> bindings = List.copyOf(context.getBeansOfType(Binding.class).values());

        assertThat(bindings).extracting(b -> b.getExchange() + "|" + b.getRoutingKey() + "|" + b.getDestination())
                .containsExactlyInAnyOrder(
                        "orders.exchange|orders.created|orders.created.queue",
                        "orders.exchange|orders.#|orders.audit.queue",
                        "orders.exchange|orders.created|orders.inventory.queue",
                        "orders.dlx|orders.created.dead|orders.created.dlq",
                        "orders.dlx|orders.audit.dead|orders.audit.dlq",
                        "orders.dlx|orders.inventory.dead|orders.inventory.dlq");
    }
}
