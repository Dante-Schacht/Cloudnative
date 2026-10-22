package com.cloudorders.notifications.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registro en memoria de eventos ya procesados con éxito (acotado, descarta los más antiguos).
 * Limitación: se pierde al reiniciar el servicio.
 */
public class ProcessedEventStore {

    private final Set<String> processed;

    public ProcessedEventStore(int capacity) {
        this.processed = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        }));
    }

    public boolean contains(String key) {
        return processed.contains(key);
    }

    public void markProcessed(String key) {
        processed.add(key);
    }
}
