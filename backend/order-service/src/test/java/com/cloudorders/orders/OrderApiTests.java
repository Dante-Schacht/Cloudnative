package com.cloudorders.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudorders.orders.messaging.EventPublicationException;
import com.cloudorders.orders.messaging.OrderEventPublisher;
import com.cloudorders.orders.model.Order;
import com.cloudorders.orders.model.OrderStatus;
import com.cloudorders.orders.repository.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Contrato HTTP de pedidos con un usuario autenticado que tiene ambos scopes (la seguridad se prueba aparte). */
@SpringBootTest
@ActiveProfiles("test")
class OrderApiTests {

    private static final String VALID_ORDER = """
            {"customerName":"Juan Pérez","product":"Notebook","quantity":2,"notes":"Entrega por la tarde"}""";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderJpaRepository repository;

    @MockitoBean
    private OrderEventPublisher eventPublisher;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_orders.read"),
                        new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .build();
    }

    @Test
    void publishesOrderCreatedEventForAcceptedOrder() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated());

        ArgumentCaptor<Order> published = ArgumentCaptor.forClass(Order.class);
        verify(eventPublisher).publishOrderCreated(published.capture());
        assertThat(published.getValue().customerName()).isEqualTo("Juan Pérez");
        assertThat(published.getValue().status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void failsWithServiceUnavailableAndRollsBackWhenEventIsNotConfirmed() throws Exception {
        doThrow(new EventPublicationException("sin confirmación")).when(eventPublisher).publishOrderCreated(any());

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no fue registrado")));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void doesNotPublishWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"Notebook\",\"quantity\":2}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("order-service"));
    }

    @Test
    void listIsEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createsValidOrder() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerName").value("Juan Pérez"))
                .andExpect(jsonPath("$.product").value("Notebook"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.notes").value("Entrega por la tarde"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T.*(Z|[+-]\\d{2}:\\d{2})")));
    }

    @Test
    void rejectsMissingCustomerName() throws Exception {
        String body = """
                {"product":"Notebook","quantity":2}""";
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("customerName")));
    }

    @Test
    void rejectsZeroQuantity() throws Exception {
        String body = """
                {"customerName":"Juan","product":"Notebook","quantity":0}""";
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    void rejectsDecimalQuantity() throws Exception {
        String body = """
                {"customerName":"Juan","product":"Notebook","quantity":1.5}""";
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createdOrdersAreListed() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Juan Pérez"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void allowsCorsFromFrontendOnly() throws Exception {
        mockMvc.perform(options("/api/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/orders")
                        .header("Origin", "http://malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
