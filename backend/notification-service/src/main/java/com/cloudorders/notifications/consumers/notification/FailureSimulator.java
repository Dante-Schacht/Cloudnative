package com.cloudorders.notifications.consumers.notification;

import com.cloudorders.messaging.OrderCreatedEvent;
import com.cloudorders.notifications.config.ProcessingProperties;
import com.cloudorders.notifications.processing.NonRecoverableEventException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Provoca fallos controlados SOLO si cloudorders.processing.simulation.enabled=true, mediante marcas
 * en las observaciones del pedido: "[simulate:transient:N]" falla los primeros N intentos y
 * "[simulate:fatal]" falla de forma no recuperable.
 */
@Component
public class FailureSimulator {

    private static final Pattern TRANSIENT = Pattern.compile("\\[simulate:transient:(\\d+)]");
    private static final String FATAL = "[simulate:fatal]";

    private final boolean enabled;
    private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

    public FailureSimulator(ProcessingProperties properties) {
        this.enabled = properties.simulationEnabled();
    }

    public void maybeFail(OrderCreatedEvent event) {
        String notes = event.notes();
        if (!enabled || notes == null) {
            return;
        }
        if (notes.contains(FATAL)) {
            throw new NonRecoverableEventException("Fallo no recuperable simulado");
        }
        Matcher matcher = TRANSIENT.matcher(notes);
        if (matcher.find()) {
            int failures = Integer.parseInt(matcher.group(1));
            if (attempts.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet() <= failures) {
                throw new IllegalStateException("Fallo transitorio simulado");
            }
        }
    }
}
