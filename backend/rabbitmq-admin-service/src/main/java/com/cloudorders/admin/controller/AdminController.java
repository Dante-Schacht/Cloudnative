package com.cloudorders.admin.controller;

import com.cloudorders.admin.dto.BindingRequest;
import com.cloudorders.admin.dto.CreateExchangeRequest;
import com.cloudorders.admin.dto.CreateQueueRequest;
import com.cloudorders.admin.dto.ExchangeResponse;
import com.cloudorders.admin.dto.QueueResponse;
import com.cloudorders.admin.dto.QueueStatsResponse;
import com.cloudorders.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de administración de RabbitMQ (requiere HTTP Basic, rol ADMIN). Solo opera sobre recursos con el prefijo
 * gestionado:
 * <ul>
 *   <li>{@code POST /api/admin/queues}, {@code GET|DELETE /api/admin/queues/{name}}</li>
 *   <li>{@code POST /api/admin/exchanges}, {@code DELETE /api/admin/exchanges/{name}}</li>
 *   <li>{@code POST|DELETE /api/admin/bindings} (exchange, queue y routingKey en el cuerpo)</li>
 * </ul>
 * Toda la lógica de RabbitMQ está en {@link RabbitAdminService}; el controlador solo valida y delega.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RabbitAdminService service;

    public AdminController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueResponse createQueue(@Valid @RequestBody CreateQueueRequest request) {
        return service.createQueue(request);
    }

    @GetMapping("/queues/{name}")
    public QueueStatsResponse getQueue(@PathVariable String name) {
        return service.getQueue(name);
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable String name) {
        service.deleteQueue(name);
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeResponse createExchange(@Valid @RequestBody CreateExchangeRequest request) {
        return service.createExchange(request);
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable String name) {
        service.deleteExchange(name);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingRequest createBinding(@Valid @RequestBody BindingRequest request) {
        return service.createBinding(request);
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@Valid @RequestBody BindingRequest request) {
        service.deleteBinding(request);
    }
}
