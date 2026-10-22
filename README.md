# CloudOrders – Mensajería con RabbitMQ (EP3)

Proyecto académico de Desarrollo Cloud Native I. Frontend React + Vite (`CloudOrders/frontend`) y backend Spring Boot (Java 21) en `backend/`.

## Arquitectura

```
React (5173) ──HTTP──> order-service (8080) ──publica ORDER_CREATED──> RabbitMQ (clúster de 2 nodos)
                                                                          │ orders.exchange (topic)
                                                                          ├─> orders.created.queue   ─> notification-service (notificación simulada)
                                                                          ├─> orders.audit.queue     ─> notification-service (auditoría)
                                                                          └─> orders.inventory.queue ─> notification-service (reserva simulada)
                                        orders.dlx (direct) <── NACK / reintentos agotados ── DLQ por cola
rabbitmq-admin-service (8082): API REST (HTTP Basic) para administrar colas, exchanges y bindings
```

| Módulo (`backend/`) | Rol |
|---|---|
| `messaging-contract` | `OrderCreatedEvent`, único contrato compartido entre servicios |
| `order-service` | API de pedidos existente + productor con *publisher confirms*; declara la topología |
| `notification-service` | Consumidor con ACK manual, reintentos acotados y NACK a DLQ |
| `rabbitmq-admin-service` | Administración de RabbitMQ con DTO validados y autenticación |

## Ejecución local

```powershell
copy .env.example .env      # reemplazar TODOS los "change-me" por secretos propios (.env no se sube a git)
docker compose up -d --build
docker compose ps           # los 5 contenedores deben quedar "healthy"
docker compose exec rabbitmq1 rabbitmqctl cluster_status   # debe listar rabbit@rabbitmq1 y rabbit@rabbitmq2 como Running Nodes
```

- Management UI: http://localhost:15672 (nodo 1) y http://localhost:15673 (nodo 2), con `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` de `.env`.
- Puertos publicados solo en `127.0.0.1`. Detener: `docker compose down` (añadir `-v` para borrar datos del broker).
- Frontend: `cd CloudOrders/frontend; npm install; npm run dev` (`VITE_API_BASE_URL=http://localhost:8080`).
- Pruebas: `cd backend/order-service; .\mvnw.cmd -f ..\pom.xml test` (ejecuta los 3 servicios; no requieren broker).

## Topología (nombres centralizados en `application.yml`)

| Recurso | Tipo / valor |
|---|---|
| `orders.exchange` | topic, durable |
| `orders.dlx` | direct, durable (dead-letter exchange) |
| `orders.created.queue` ← `orders.created` | quorum, DLX `orders.dlx` / `orders.created.dead` → `orders.created.dlq` |
| `orders.audit.queue` ← `orders.#` | quorum, DLX `orders.dlx` / `orders.audit.dead` → `orders.audit.dlq` |
| `orders.inventory.queue` ← `orders.created` | quorum, DLX `orders.dlx` / `orders.inventory.dead` → `orders.inventory.dlq` |

Los beans `Exchange`, `Queue` y `Binding` están declarados explícitamente en [`RabbitTopologyConfig`](backend/order-service/src/main/java/com/cloudorders/orders/config/RabbitTopologyConfig.java) y toman sus nombres de `cloudorders.rabbitmq.*` ([`RabbitTopologyProperties`](backend/order-service/src/main/java/com/cloudorders/orders/config/RabbitTopologyProperties.java)). Las colas son *quorum* para replicarse en ambos nodos.

## Evento `ORDER_CREATED`

Campos reales del pedido: `eventId` (UUID), `eventType`, `orderId`, `customerName`, `product`, `quantity`, `notes`, `status`, `createdAt`. El contrato HTTP de `POST/GET /api/orders` **no cambió**.

## Fiabilidad

- **Productor:** `publisher-confirm-type: correlated`, `publisher-returns` y `mandatory`. `POST /api/orders` solo responde 201 si el broker confirma (ACK) y el mensaje es enrutable.
- **Fallo de publicación:** se revierte el guardado del pedido y se responde **HTTP 503** (no se deja creer que el evento fue entregado).
- **Consumidor:** `AcknowledgeMode.MANUAL`. Éxito → `basicAck`. Mensaje inválido o fallo no recuperable → `basicNack(requeue=false)` → DLQ. Fallo transitorio → hasta 3 intentos (1 s entre intentos) y luego NACK sin requeue. No hay requeue infinito.
- **Idempotencia:** `eventId` ya procesado por un consumidor se confirma sin reprocesar (registro en memoria, acotado).
- `cloudorders.processing.simulation.enabled` (por defecto `false`) permite provocar fallos con marcas en `notes`: `[simulate:transient:N]` o `[simulate:fatal]`. Solo para pruebas locales.

## API de administración (`rabbitmq-admin-service`, puerto 8082)

HTTP Basic con `ADMIN_API_USERNAME` / `ADMIN_API_PASSWORD` (mín. 12 caracteres; sin valores por defecto). Solo opera sobre recursos con prefijo `sandbox.` (configurable en `cloudorders.admin.managed-prefix`), por lo que no puede alterar `orders.*`.

| Método y ruta | Descripción |
|---|---|
| `POST /api/admin/queues` | Crear cola (`name`, `durable`, `type` QUORUM/CLASSIC) |
| `GET /api/admin/queues/{name}` | Mensajes y consumidores |
| `DELETE /api/admin/queues/{name}` | Eliminar (solo vacía y sin consumidores) |
| `POST /api/admin/exchanges` | Crear exchange (`name`, `type`, `durable`) |
| `DELETE /api/admin/exchanges/{name}` | Eliminar (solo sin bindings) |
| `POST` / `DELETE /api/admin/bindings` | Crear / eliminar binding (`exchange`, `queue`, `routingKey`) |

Errores: 400 (validación), 401 (sin credenciales), 403 (recurso protegido), 404, 409 (en uso / conflicto), 503 (broker no disponible).

## Verificación realizada (entorno local, 2026-10-22)

Automatizadas (JUnit, sin broker): `order-service` 15, `notification-service` 7, `rabbitmq-admin-service` 12 pruebas (34 en total) → todas pasan. `npm run build` del frontend → correcto. La auditoría final está en [AUDITORIA_FINAL_EP3_CLOUDORDERS.md](AUDITORIA_FINAL_EP3_CLOUDORDERS.md).

Manuales contra el clúster Docker real (RabbitMQ 4.1.8, 2 nodos):
1. Clúster: `cluster_status` lista ambos nodos como *Disk Nodes* y *Running Nodes*, sin particiones.
2. Topología: exchanges, 6 colas quorum con miembros en ambos nodos, bindings y argumentos DLX consultados por la API de management.
3. Pedido → evento confirmado → 3 ACK, correlacionados por `eventId`/`orderId` en los logs.
4. Fallo transitorio (1 fallo) se recupera; 5 fallos agotan 3 intentos → DLQ; fallo fatal → DLQ; JSON roto y evento inválido → NACK sin requeue → DLQ de las 3 colas.
5. API de administración: 401/400/403/404/409/201/204 según lo esperado, incluida la protección de `orders.*`.
6. Pedido inválido → 400; CORS desde `http://localhost:5173` correcto.
7. Caída de 1 nodo y de ambos: `POST /api/orders` → 503 sin conservar el pedido; tras reiniciar, el clúster y el flujo se recuperan.

No se verificó visualmente la Management UI (se consultó su API) ni el frontend en un navegador (el código del frontend no se modificó).

## Limitaciones conocidas / pendiente

- **Persistencia en memoria** (`InMemoryOrderRepository`): los pedidos se pierden al reiniciar. **No hay patrón outbox**: guardar el pedido y publicar el evento no es atómico. Si el broker confirma tarde o falla tras recibir el mensaje, puede existir un evento de un pedido revertido. Los IDs de pedidos revertidos no se reutilizan (hay saltos).
- **Clúster de 2 nodos:** las colas quorum necesitan mayoría; con un nodo caído no se confirman publicaciones (verificado: 503). Tolerar la caída de un nodo requiere 3 nodos.
- Idempotencia en memoria (se pierde al reiniciar el consumidor); los tres listeners conviven en `notification-service`.
- El nombre lógico del clúster no se fija por configuración: tras formarse el clúster se unificó con `rabbitmqctl set_cluster_name cloudorders` (cosmético; la membresía y la replicación no dependen de él).
- La API de administración usa HTTP Basic sobre HTTP y el mismo usuario de broker que la aplicación; en producción usar TLS, usuarios con mínimo privilegio y un gestor de secretos.
- No implementados: base de datos cloud, JWT, despliegue en la nube. No se pudo contrastar la rúbrica oficial (el PDF no está en el repositorio).
