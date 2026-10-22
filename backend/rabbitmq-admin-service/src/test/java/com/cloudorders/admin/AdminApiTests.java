package com.cloudorders.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTests {

    private static final RequestPostProcessor ADMIN = httpBasic("admin-test", "test-password-123");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void rejectsRequestsWithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sandbox.q\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/queues/sandbox.q"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rejectsWrongCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/queues/sandbox.q").with(httpBasic("admin-test", "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsQuorumQueueByDefault() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sandbox.demo.queue\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("sandbox.demo.queue"))
                .andExpect(jsonPath("$.type").value("QUORUM"))
                .andExpect(jsonPath("$.durable").value(true));

        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(queue.capture());
        assertThat(queue.getValue().getName()).isEqualTo("sandbox.demo.queue");
        assertThat(queue.getValue().getArguments()).containsEntry("x-queue-type", "quorum");
    }

    @Test
    void rejectsBlankQueueNameWithoutTouchingBroker() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rejectsInconsistentQueueConfiguration() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sandbox.q\",\"durable\":false,\"type\":\"QUORUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("quorum")));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rejectsInvalidExchangeType() throws Exception {
        mockMvc.perform(post("/api/admin/exchanges").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sandbox.ex\",\"type\":\"RARO\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void protectsResourcesOutsideTheManagedPrefix() throws Exception {
        mockMvc.perform(post("/api/admin/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"orders.created.queue\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/queues/orders.created.queue").with(ADMIN))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/exchanges/orders.exchange").with(ADMIN))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"orders.exchange\",\"queue\":\"orders.created.queue\","
                                + "\"routingKey\":\"orders.created\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(amqpAdmin);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void deletesOnlyUnusedAndEmptyQueue() throws Exception {
        when(amqpAdmin.getQueueProperties("sandbox.demo.queue")).thenReturn(queueStats(0, 0));

        mockMvc.perform(delete("/api/admin/queues/sandbox.demo.queue").with(ADMIN))
                .andExpect(status().isNoContent());

        verify(amqpAdmin).deleteQueue("sandbox.demo.queue");
    }

    @Test
    void returnsNotFoundWhenDeletingMissingQueue() throws Exception {
        when(amqpAdmin.getQueueProperties("sandbox.missing")).thenReturn(null);

        mockMvc.perform(delete("/api/admin/queues/sandbox.missing").with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictAndKeepsQueueWhenItHasMessagesOrConsumers() throws Exception {
        when(amqpAdmin.getQueueProperties("sandbox.busy")).thenReturn(queueStats(3, 1));

        mockMvc.perform(delete("/api/admin/queues/sandbox.busy").with(ADMIN))
                .andExpect(status().isConflict());

        verify(amqpAdmin, never()).deleteQueue(any(String.class));
    }

    private static Properties queueStats(int messages, int consumers) {
        Properties properties = new Properties();
        properties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, messages);
        properties.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumers);
        return properties;
    }

    @Test
    void createsAndDeletesBindingIdentifiedByExchangeQueueAndRoutingKey() throws Exception {
        String body = "{\"exchange\":\"sandbox.ex\",\"queue\":\"sandbox.q\",\"routingKey\":\"sandbox.key\"}";

        mockMvc.perform(post("/api/admin/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("sandbox.key"));
        mockMvc.perform(delete("/api/admin/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("sandbox.ex");
        assertThat(binding.getValue().getDestination()).isEqualTo("sandbox.q");
        assertThat(binding.getValue().getRoutingKey()).isEqualTo("sandbox.key");
        verify(amqpAdmin).removeBinding(any(Binding.class));
    }

    @Test
    void rejectsBindingWithMissingFields() throws Exception {
        mockMvc.perform(post("/api/admin/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"sandbox.ex\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        verifyNoInteractions(amqpAdmin);
    }
}
