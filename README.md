# CloudOrders – Cloud Native: RabbitMQ, PostgreSQL, JWT (Entra ID) y API Gateway

Proyecto académico de Desarrollo Cloud Native I (DSY1107, Duoc UC) de Dante Schacht y Natanael Reveco. Frontend React + Vite (`frontend/`) y backend Spring Boot 3.5 / Java 21 (`backend/`).

> **Estado:** implementado y verificado en local: RabbitMQ (clúster de 2 nodos), PostgreSQL (JPA + Flyway), validación JWT en el backend (Spring Security) y login MSAL en el frontend (código listo). **Pendiente de tu autorización/acción:** tenant y registros de Entra ID, y todos los recursos cloud (Cloud SQL, Cloud Run, API Gateway, VM). Ver [docs/ARQUITECTURA_CLOUD.md](docs/ARQUITECTURA_CLOUD.md) (arquitectura, costos, registro en Entra y comandos de despliegue).

## Arquitectura

```
React (5173) ─login OIDC/PKCE─> Microsoft Entra ID ─> access token JWT
     │  Authorization: Bearer <JWT>
     ▼
order-service (8080; valida JWT y scopes) ─JPA/Flyway─> PostgreSQL
     │ publica ORDER_CREATED
     ▼
RabbitMQ (clúster de 2 nodos)
     │ orders.exchange (topic)
     ├─> orders.created.queue   ─> notification-service (notificación simulada)
     ├─> orders.audit.queue     ─> notification-service (auditoría)
     └─> orders.inventory.queue ─> notification-service (reserva simulada)
orders.dlx (direct) <── NACK / reintentos agotados ── DLQ por cola
rabbitmq-admin-service (8082): API REST (HTTP Basic) para administrar colas, exchanges y bindings
Despliegue cloud (propuesto): frontend y order-service en Cloud Run, Google Cloud API Gateway delante del backend, Cloud SQL, RabbitMQ en una VM con Docker Compose.
```

| Módulo (`backend/`) | Rol |
|---|---|
| `messaging-contract` | `OrderCreatedEvent`, único contrato compartido entre servicios |
| `order-service` | API de pedidos (JPA/PostgreSQL, OAuth2 Resource Server) + productor con *publisher confirms*; declara la topología |
| `notification-service` | Consumidor con ACK manual, reintentos acotados y NACK a DLQ |
| `rabbitmq-admin-service` | Administración de RabbitMQ con DTO validados y autenticación |

| Otros directorios | Contenido |
|---|---|
| `frontend/` | React + Vite, MSAL (`src/auth`), `Dockerfile` y `nginx.conf` |
| `infra/rabbitmq/` | Configuración del clúster RabbitMQ |
| `infra/gcp/` | `docker-compose.vm.yml` (VM con RabbitMQ) y `openapi-gateway.yaml.tpl` (API Gateway) |
| `docs/` | Arquitectura cloud, costos, Entra ID, despliegue y propuesta de *outbox* |

## Ejecución local

```powershell
copy .env.example .env      # reemplazar TODOS los "change-me" por secretos propios (.env no se sube a git)
docker compose up -d --build
docker compose ps           # los 6 contenedores deben quedar "healthy" (RabbitMQ x2, PostgreSQL, 3 servicios)
docker compose exec rabbitmq1 rabbitmqctl cluster_status   # debe listar rabbit@rabbitmq1 y rabbit@rabbitmq2 como Running Nodes
```

- Management UI: http://localhost:15672 (nodo 1) y http://localhost:15673 (nodo 2), con `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` de `.env`.
- PostgreSQL local: `127.0.0.1:5432` (`POSTGRES_*` de `.env`). El esquema lo crea Flyway (`backend/order-service/src/main/resources/db/migration`). Los datos persisten en el volumen `postgres-data`.
- Puertos publicados solo en `127.0.0.1`. Detener: `docker compose down` (añadir `-v` borra los datos del broker **y de PostgreSQL**).
- `GET`/`POST /api/orders` exigen un access token JWT de Entra ID: **sin `ENTRA_TENANT_ID` y `ENTRA_API_AUDIENCE` responden 401**. `/api/health` es público.
- Entra ID: tras `az login --allow-no-subscriptions`, `.\infra\entra\setup-entra.ps1` registra las apps API y SPA, los scopes `orders.read`/`orders.write` y un usuario de prueba, y completa `.env` y `frontend/.env` (la contraseña del usuario queda en `.secrets/`, ignorado por git).
- Frontend: `cd frontend; copy .env.example .env; npm install; npm run dev` (completar `VITE_ENTRA_*` tras registrar las apps en Entra ID).
- Pruebas: `cd backend/order-service; .\mvnw.cmd -f ..\pom.xml test` (ejecuta los 3 servicios; el test de integración con PostgreSQL usa Testcontainers y se omite si Docker no está disponible; ninguno requiere RabbitMQ).

## Variables de entorno

| Variable | Dónde | Descripción |
|---|---|---|
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_ERLANG_COOKIE` | `.env` | Clúster RabbitMQ |
| `ADMIN_API_USERNAME`, `ADMIN_API_PASSWORD` | `.env` | API de administración (HTTP Basic) |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `.env` | PostgreSQL local |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | `order-service` | Conexión JDBC (en cloud: Cloud SQL, perfil `cloud`) |
| `ENTRA_TENANT_ID`, `ENTRA_API_AUDIENCE` | `.env` / `order-service` | Tenant y audience (client ID de la API) para validar JWT |
| `VITE_API_BASE_URL` | `frontend/.env` | Backend en local; **URL de API Gateway en el despliegue cloud** |
| `VITE_ENTRA_CLIENT_ID`, `VITE_ENTRA_TENANT_ID`, `VITE_ENTRA_API_SCOPES` | `frontend/.env` | Login MSAL (valores públicos; sin secretos) |

## Seguridad (Spring Security + Entra ID)

`order-service` es un OAuth2 Resource Server. Autenticación en [`JwtValidationConfig`](backend/order-service/src/main/java/com/cloudorders/orders/config/JwtValidationConfig.java) (firma con el JWKS de Entra, issuer, expiración y audience) y autorización en [`SecurityConfig`](backend/order-service/src/main/java/com/cloudorders/orders/config/SecurityConfig.java): `GET /api/orders` exige el scope `orders.read` y `POST /api/orders` el scope `orders.write`. Cómo registrar las aplicaciones en Entra ID: [docs/ARQUITECTURA_CLOUD.md §4](docs/ARQUITECTURA_CLOUD.md).

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

Automatizadas (JUnit; ninguna requiere RabbitMQ): `order-service` 26 (12 contrato HTTP, 10 seguridad JWT/scopes, 3 topología, 1 integración con PostgreSQL real vía Testcontainers), `notification-service` 7, `rabbitmq-admin-service` 12 (45 en total, todas pasan el 2026-10-22). Las pruebas de seguridad firman los tokens con una clave RSA generada en la prueba: validan la lógica (sin token, token inválido, expirado, audience/issuer incorrectos, scopes suficientes e insuficientes), **no** sustituyen la verificación con tokens reales de Entra ID. `npm run build` del frontend → correcto. La imagen del frontend (`frontend/Dockerfile`) se construyó y respondió 200 en local. La auditoría EP3 previa está en [AUDITORIA_FINAL_EP3_CLOUDORDERS.md](AUDITORIA_FINAL_EP3_CLOUDORDERS.md) (anterior a PostgreSQL y JWT).

Verificado en local con Docker tras añadir PostgreSQL y seguridad: Flyway aplica `V1` en PostgreSQL 17; los pedidos persisten tras reiniciar `order-service` (comprobado a nivel de base de datos); sin token `GET`/`POST /api/orders` → 401; `/api/health` → 200; el preflight CORS permite `Authorization`; el clúster RabbitMQ y las 6 colas quorum con sus 3 consumidores siguen intactos.

**No verificado aún (requiere tenant y recursos cloud):** login real con Entra ID, token real aceptado por el backend, API Gateway, Cloud SQL, Cloud Run, VM en la nube, y el flujo extremo a extremo posterior a los cambios de PostgreSQL/JWT.

Manuales contra el clúster Docker real (RabbitMQ 4.1.8, 2 nodos):
1. Clúster: `cluster_status` lista ambos nodos como *Disk Nodes* y *Running Nodes*, sin particiones.
2. Topología: exchanges, 6 colas quorum con miembros en ambos nodos, bindings y argumentos DLX consultados por la API de management.
3. Pedido → evento confirmado → 3 ACK, correlacionados por `eventId`/`orderId` en los logs.
4. Fallo transitorio (1 fallo) se recupera; 5 fallos agotan 3 intentos → DLQ; fallo fatal → DLQ; JSON roto y evento inválido → NACK sin requeue → DLQ de las 3 colas.
5. API de administración: 401/400/403/404/409/201/204 según lo esperado, incluida la protección de `orders.*`.
6. Pedido inválido → 400; CORS desde `http://localhost:5173` correcto.
7. Caída de 1 nodo y de ambos: `POST /api/orders` → 503 sin conservar el pedido; tras reiniciar, el clúster y el flujo se recuperan.

No se verificó visualmente la Management UI (se consultó su API). El frontend se compiló, pero el login con MSAL no se ha probado en un navegador contra Entra ID.

## Limitaciones conocidas / pendiente

- **Sin patrón outbox** (propuesta en [docs/ARQUITECTURA_CLOUD.md §6](docs/ARQUITECTURA_CLOUD.md), pendiente de decisión): guardar el pedido y publicar el evento no es atómico. Si el broker confirma tarde o falla tras recibir el mensaje, puede existir un evento de un pedido revertido. Los IDs de pedidos revertidos no se reutilizan (hay saltos).
- **Clúster de 2 nodos:** las colas quorum necesitan mayoría; con un nodo caído no se confirman publicaciones (verificado: 503). Tolerar la caída de un nodo requiere 3 nodos. En la VM cloud ambos nodos comparten máquina: cumplen el requisito de clúster, no ofrecen alta disponibilidad real.
- Idempotencia en memoria (se pierde al reiniciar el consumidor); los tres listeners conviven en `notification-service`.
- El nombre lógico del clúster no se fija por configuración: tras formarse el clúster se unificó con `rabbitmqctl set_cluster_name cloudorders` (cosmético; la membresía y la replicación no dependen de él).
- La API de administración usa HTTP Basic sobre HTTP y el mismo usuario de broker que la aplicación; en producción usar TLS, usuarios con mínimo privilegio y un gestor de secretos.
- El estado de los pedidos permanece en `PENDING`: ningún consumidor lo actualiza.
- Frontend: la rúbrica EP3 pide un componente **Angular**; el proyecto usa React (la autorización del docente debe confirmarse por escrito).
- No implementados: recursos cloud (Cloud SQL, Cloud Run, API Gateway, VM), tenant y usuarios de Entra ID.

## Lista de evidencias para la presentación (EP4)

| # | Evidencia | Estado |
|---|---|---|
| 1 | Frontend desplegado en Cloud Run | Pendiente (imagen lista) |
| 2 | Login real con Entra ID (tenant y usuarios visibles) | Pendiente |
| 3 | Access token JWT obtenido y validado por el backend | Pendiente |
| 4 | API Gateway: 401 sin token/inválido, 200 con token válido; el frontend usa la URL del gateway | Pendiente (especificación lista) |
| 5 | Backend desplegado (Cloud Run) | Pendiente |
| 6 | PostgreSQL cloud (Cloud SQL) con los pedidos | Pendiente (local verificado) |
| 7 | RabbitMQ Management con dos nodos y `docker compose` en la VM | Pendiente (local verificado) |
| 8 | Tres colas y tres DLQ | Verificado en local |
| 9 | Exchanges `direct` (`orders.dlx`) y `topic` (`orders.exchange`) | Verificado en local |
| 10 | Pedido de extremo a extremo (login → gateway → backend → PostgreSQL → RabbitMQ → 3 consumidores) | Pendiente |
