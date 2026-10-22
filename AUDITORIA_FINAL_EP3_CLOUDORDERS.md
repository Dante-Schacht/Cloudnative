# Auditoría final EP3 — CloudOrders (RabbitMQ)

| | |
|---|---|
| **Asignatura** | Desarrollo Cloud Native I (DSY1107) — Duoc UC |
| **Evaluación** | Evaluación Parcial N° 3 (EP3) — Encargo, en parejas |
| **Integrantes** | Dante Schacht y Natanael Reveco |
| **Fecha de la auditoría** | 2026-10-22 (entorno local Windows, Docker Desktop 29.8.2) |
| **Repositorio** | `Dante-Schacht/Cloudnative` (sin commits ni push realizados en esta ejecución) |
| **Fuente de la rúbrica** | PDF oficial `EP3_DSY1107_Estudiante_encargo.pdf` (7 páginas; texto extraído y leído completo) |

> **Cómo leer este informe.** Cada afirmación está marcada como **[VERIFICADO]** (comprobado ejecutando comandos/pruebas en esta sesión), **[INFERIDO]** (deducido leyendo el código, sin ejecutarlo) o **[NO COMPROBADO]**. No se simuló ningún resultado.

---

## 1. Resumen ejecutivo

- RabbitMQ está integrado de extremo a extremo y **funciona en un clúster real de dos nodos**: productor con *publisher confirms*, tres consumidores con ACK manual, reintentos acotados, DLQ por cola y un microservicio administrador autenticado. **[VERIFICADO]**
- **34 pruebas automáticas aprobadas** (15 + 7 + 12) y `npm run build` correcto. **[VERIFICADO]**
- El frontend React + Vite se abrió en un navegador real: conecta con el backend, valida el formulario, crea un pedido y lo muestra en la tabla; ese pedido generó el evento y 3 ACK. **[VERIFICADO]**
- Se encontraron y corrigieron **2 hallazgos de rúbrica** (beans individuales de Queue/Exchange/Binding y agrupación de consumidores por dominio) y **1 anomalía cosmética del clúster** (nombre del clúster distinto en cada nodo).
- Los 8 indicadores EP3 quedan evaluados como **CUMPLIDO** (ver §6). Con reservas explícitas en §9 (requisitos generales del PDF que **no** forman parte de los 8 indicadores y siguen pendientes: Angular, IDaaS/JWT, base de datos cloud).

---

## 2. Verificaciones ejecutadas

| # | Fase | Verificación | Resultado |
|---|---|---|---|
| 1 | Docker | Docker Desktop operativo (`docker info`), `docker-compose.yml` localizado, `docker compose ps` | OK — 5/5 contenedores `healthy`, 0 reinicios **[VERIFICADO]** |
| 2 | Docker | Healthchecks (`docker inspect`) y logs de los servicios | OK; 0 líneas ERROR/WARN recientes en los 3 servicios **[VERIFICADO]** |
| 3 | Docker | Volúmenes `cloudorders_rabbitmq1-data` y `rabbitmq2-data` | Intactos; no se eliminó ningún volumen **[VERIFICADO]** |
| 4 | RabbitMQ | `rabbitmqctl cluster_status` en ambos nodos | Mismos *Disk Nodes* y *Running Nodes*; sin alarmas ni particiones **[VERIFICADO]** |
| 5 | RabbitMQ | `rabbitmq-diagnostics check_running` y `check_local_alarms` | OK en ambos nodos **[VERIFICADO]** |
| 6 | RabbitMQ | API de management: colas, tipo, miembros, líder, DLX, exchanges, bindings, conexiones, consumidores | Ver §4 **[VERIFICADO]** |
| 7 | RabbitMQ | Búsqueda de literales de topología en el código | Solo aparecen en pruebas; el código principal los lee de `application.yml` **[VERIFICADO]** |
| 8 | Pruebas | `mvnw clean test` sobre los 3 microservicios (+ módulo de contrato) | 34/34 OK **[VERIFICADO]** |
| 9 | Pruebas | `npm run build` del frontend | OK (24 módulos, 229,55 kB JS) **[VERIFICADO]** |
| 10 | Funcional | `GET /api/health`, `GET /api/orders`, `POST /api/orders` | 200 / 200 / 201 **[VERIFICADO]** |
| 11 | Funcional | Contrato HTTP del pedido | Campos `id, customerName, product, quantity, notes, status, createdAt` sin cambios **[VERIFICADO]** |
| 12 | Funcional | Publicación de `ORDER_CREATED` y confirmación del broker | Log `publicado y confirmado` con `eventId`/`orderId` **[VERIFICADO]** |
| 13 | Funcional | Recepción en los 3 consumidores + ACK | 3 logs de ACK por el mismo `eventId` (intento 1) **[VERIFICADO]** |
| 14 | Funcional | Mensaje inválido → NACK sin requeue → DLQ | Las 3 DLQ subieron +1 (audit 2→3, created 4→5, inventory 2→3) **[VERIFICADO]** |
| 15 | Funcional | API administrativa (401, 400, 403, 201, 204 sobre recursos `sandbox.*`) | Respuestas esperadas; recursos de prueba eliminados (0 restantes) **[VERIFICADO]** |
| 16 | Frontend | Navegador real: estado del backend, validación, alta de pedido, tabla | OK (ver §5) **[VERIFICADO]** |
| 17 | Auditoría | Revisión del código contra los 8 indicadores del PDF | Ver §6 |
| 18 | Regresión | Tras cada corrección: pruebas completas, redeploy de los servicios y flujo en vivo (6 colas quorum, 3 consumidores, 3 ACK) | OK **[VERIFICADO]** |

Verificaciones adicionales **de la implementación previa** que no se repitieron en esta ejecución (para no generar datos ni interrupciones innecesarias) y que se documentan en el `README.md`: reintentos con fallo transitorio, fallo fatal, caída de uno y de ambos nodos (503 sin conservar el pedido). Siguen siendo válidas porque la lógica involucrada no cambió. **[INFERIDO para esta ejecución]**

---

## 3. Contenedores y estado final

| Contenedor | Servicio | Estado | Puertos (solo `127.0.0.1`) |
|---|---|---|---|
| `cloudorders-rabbitmq1-1` | `rabbitmq1` (RabbitMQ 4.1.8 / Erlang 27.3.4.19) | `healthy` | 5672, 15672 |
| `cloudorders-rabbitmq2-1` | `rabbitmq2` (RabbitMQ 4.1.8 / Erlang 27.3.4.19) | `healthy` | 5673, 15673 |
| `cloudorders-order-service-1` | `order-service` | `healthy` | 8080 |
| `cloudorders-notification-service-1` | `notification-service` | `healthy` | 8081 |
| `cloudorders-rabbitmq-admin-service-1` | `rabbitmq-admin-service` | `healthy` | 8082 |

---

## 4. Clúster RabbitMQ, colas, exchanges y bindings **[VERIFICADO]**

**Clúster.** Ambos nodos (`rabbit@rabbitmq1`, `rabbit@rabbitmq2`) figuran como *Disk Nodes* y *Running Nodes* desde ambos puntos de vista; *Network Partitions* y *Alarms*: ninguna. Las colas quorum tienen como miembros a los dos nodos y la API responde igual en los puertos 15672 y 15673.

**Colas** (todas `quorum`, `durable`, `state=running`, miembros `rabbit@rabbitmq1 + rabbit@rabbitmq2`):

| Cola | Rol | DLX → routing key de muerte | Consumidores | ACK |
|---|---|---|---|---|
| `orders.created.queue` | principal (notificación) | `orders.dlx` → `orders.created.dead` | 1 | manual, prefetch 10 |
| `orders.audit.queue` | principal (auditoría) | `orders.dlx` → `orders.audit.dead` | 1 | manual, prefetch 10 |
| `orders.inventory.queue` | principal (inventario) | `orders.dlx` → `orders.inventory.dead` | 1 | manual, prefetch 10 |
| `orders.created.dlq` | DLQ | — | 0 | — |
| `orders.audit.dlq` | DLQ | — | 0 | — |
| `orders.inventory.dlq` | DLQ | — | 0 | — |

**Exchanges:** `orders.exchange` (**topic**, durable) y `orders.dlx` (**direct**, durable).

**Bindings:**

```text
orders.exchange --[orders.created]--> orders.created.queue
orders.exchange --[orders.#]--------> orders.audit.queue
orders.exchange --[orders.created]--> orders.inventory.queue
orders.dlx      --[orders.created.dead]--> orders.created.dlq
orders.dlx      --[orders.audit.dead]----> orders.audit.dlq
orders.dlx      --[orders.inventory.dead]-> orders.inventory.dlq
```

**Conexiones:** 2 conexiones `running` del usuario de aplicación (consumidores y productor); la del administrador se abre bajo demanda.

**Estado actual de las DLQ:** contienen mensajes de las pruebas (audit 3, created 5, inventory 3). No se purgaron para no destruir evidencia; son datos de prueba.

---

## 5. Frontend (React + Vite) **[VERIFICADO en navegador real]**

Se levantó `npm run dev` (Vite 8.3.4) y se abrió `http://localhost:5173/#/pedidos` en el navegador integrado, con capturas y *snapshots* de accesibilidad reales.

| Comprobación | Resultado |
|---|---|
| Indicador de estado en el encabezado | "Backend conectado" |
| Tabla de pedidos al cargar | Mostró los 7 pedidos existentes con ID, cliente, producto, cantidad, fecha y estado "Pendiente" |
| Validación del formulario (envío vacío) | Mensajes "El nombre del cliente es obligatorio", "El producto es obligatorio", "La cantidad es obligatoria" |
| Alta de pedido (cliente `Prueba UI EP3`, producto `Monitor 27`, cantidad `2`) | Mensaje "Pedido enviado correctamente."; formulario reiniciado |
| Tabla tras el alta | Apareció el pedido #10 |
| Backend tras el alta | `ORDER_CREATED` publicado y confirmado; 3 ACK de los consumidores para ese mismo pedido |

**Observaciones honestas:**
- Mi primer intento abrió la app en `http://127.0.0.1:5173` y el backend la rechazó por CORS. **No es un defecto**: el backend solo permite el origen `http://localhost:5173` por diseño (la prueba automatizada lo exige). Se repitió con el origen correcto.
- La herramienta de escritura del navegador tuvo desfases de foco; el campo "Observaciones" (opcional) quedó con texto parcial (`avegador automatizado`). Es un artefacto de la herramienta, no del frontend.
- La captura del viewport era estrecha (diseño móvil); no se evaluó el diseño en escritorio.
- No se modificó ningún archivo del frontend. El servidor Vite se detuvo al terminar (puerto 5173 libre).

---

## 6. Auditoría contra la rúbrica oficial EP3 (8 indicadores)

Clasificación pedida: **CUMPLIDO / PARCIAL / NO CUMPLIDO**. La columna "Nivel inferido" traduce la evidencia a la escala del PDF (Muy buen desempeño 100 %, Buen desempeño 80 %, Aceptable 60 %, Incipiente 30 %, No logrado 0 %); es una **estimación del auditor**, no una calificación oficial.

| # | Indicador (peso) | Estado | Nivel inferido | Evidencia verificable |
|---|---|---|---|---|
| 1 | ACK y manejo explícito de errores (20 %) | **CUMPLIDO** | Muy buen desempeño | `ManualAckEventProcessor`: `basicAck` (L69, L77), `basicNack(requeue=false)` (L59, L83, L97); distingue JSON/evento inválido, `NonRecoverableEventException`, fallo transitorio con hasta `max-attempts` y deduplicación; todo NACK se registra con ERROR. Política documentada en el Javadoc de la clase (L18–L32). Modo manual en `notification-service/application.yml` (`acknowledge-mode: manual`, `default-requeue-rejected: false`). 7 pruebas unitarias cubren ACK, NACK, reintentos, DLQ, no recuperable y duplicado. En vivo: ACK ×3 y NACK→DLQ ×3 **[VERIFICADO]** |
| 2 | Consumidores con `@RabbitListener` agrupados por dominio (15 %) | **CUMPLIDO** *(tras corrección)* | Muy buen desempeño | `consumers/notification/OrderNotificationListener` (L26), `consumers/audit/OrderAuditListener` (L24), `consumers/inventory/OrderInventoryListener` (L24): un paquete y una clase por dominio funcional. La lógica común vive en `processing/`. Antes de la corrección los tres estaban en un único paquete `listener` (ver §7) |
| 3 | Beans `Queue`, `Exchange` y `Binding` (13 %) | **CUMPLIDO** *(tras corrección)* | Muy buen desempeño | `RabbitTopologyConfig`: 2 exchanges, 6 colas y 6 bindings como `@Bean` individuales (L42–L109), uno por caso de uso (notificación, auditoría, inventario) con su DLQ. Rutas documentadas en el Javadoc de la clase. `RabbitTopologyConfigTests` (3 pruebas) comprueba los 14 beans, tipos, argumentos DLX y routing keys; en vivo, el broker mantuvo idénticas las 6 colas y 6 bindings **[VERIFICADO]** |
| 4 | Endpoints REST del administrador (13 %) | **CUMPLIDO** | Muy buen desempeño | `AdminController`: `POST /api/admin/queues`, `GET\|DELETE /api/admin/queues/{name}`, `POST /api/admin/exchanges`, `DELETE /api/admin/exchanges/{name}`, `POST\|DELETE /api/admin/bindings`; verbos y códigos HTTP coherentes (201/204/400/401/403/404/409/503); documentados en Javadoc y README. Ejecutados contra el broker real **[VERIFICADO]** |
| 5 | Nombres centralizados en `application.yml` (12 %) | **CUMPLIDO** | Muy buen desempeño | Búsqueda en todo el código Java: ningún literal `orders.*`/`sandbox.*` fuera de las pruebas. `order-service` lee `cloudorders.rabbitmq.*` (exchanges, routing key, 3 colas con binding/DLQ); `notification-service` lee `...queues.*.name` por `@RabbitListener("${...}")`; `rabbitmq-admin-service` lee `cloudorders.admin.managed-prefix`. Credenciales solo por variables de entorno |
| 6 | Separación negocio / configuración (10 %) | **CUMPLIDO** | Muy buen desempeño | `OrderService` depende de la interfaz `OrderEventPublisher`; ningún `import` de AMQP en controladores, servicios, DTO ni modelo (AMQP solo en `config/` y `messaging/`). Topología en `RabbitTopologyConfig`; seguridad en `SecurityConfig`. Reserva menor: ver §9 |
| 7 | Servicio dedicado de administración (10 %) | **CUMPLIDO** | Muy buen desempeño | Microservicio separado `rabbitmq-admin-service` con contenedor propio; toda la lógica de RabbitMQ en `RabbitAdminService` (usa `AmqpAdmin`/`RabbitTemplate`); `AdminController` solo importa DTO y el servicio |
| 8 | Validación de parámetros administrativos (7 %) | **CUMPLIDO** | Muy buen desempeño | DTO con Jakarta Validation (`@NotBlank`, `@Pattern`, `@NotNull`, `@AssertTrue` para cola quorum no durable) + `@Valid`; mensajes claros en español; prefijo gestionado y nombres inválidos rechazados en el servicio. En vivo: nombre en blanco → 400, recurso protegido → 403, sin credenciales → 401, sin tocar el broker **[VERIFICADO]** |

**Ponderación:** 20 + 15 + 13 + 13 + 12 + 10 + 10 + 7 = 100 % (coincide con el PDF).

---

## 7. Problemas encontrados y soluciones aplicadas

| # | Problema | Cómo se detectó | Solución | Verificación |
|---|---|---|---|---|
| 1 | La topología se declaraba dentro de un único bean `Declarables`; `Queue`, `Exchange` y `Binding` no eran beans individuales, como pide el indicador 3 del PDF. | Auditoría de código contra el PDF | `RabbitTopologyConfig` refactorizada a 14 `@Bean` explícitos con helpers privados (sin duplicar lógica); `RabbitTopologyProperties` pasó de `Map` a un registro `Queues(created, audit, inventory)`. **El `application.yml` no cambió.** | 34 pruebas OK; redeploy de `order-service` sin errores de declaración; broker idéntico |
| 2 | Los tres `@RabbitListener` estaban en un solo paquete `listener`, que el PDF describe como nivel "aceptable (60 %)". | Auditoría contra el PDF | Reagrupados en `consumers.notification`, `consumers.audit` y `consumers.inventory` (lógica sin cambios; `FailureSimulator` pasó al dominio de notificaciones). Se eliminaron los archivos antiguos. | Pruebas OK; redeploy; 3 consumidores activos y 3 ACK por pedido |
| 3 | El nombre lógico del clúster era distinto según el nodo (`rabbit@rabbitmq1` vs `rabbit@rabbitmq2`), aunque la membresía era idéntica. | `cluster_status` y `/api/cluster-name` | `rabbitmqctl set_cluster_name cloudorders` en caliente (cosmético, sin tocar volúmenes) | Ambos nodos devuelven `cloudorders` |
| 4 | La política de errores y las rutas de mensajería no estaban documentadas en código (el PDF valora que estén "documentadas"). | Revisión de la rúbrica | Javadoc en `ManualAckEventProcessor`, `RabbitTopologyConfig` y `AdminController`; README actualizado | Compila y pasa las pruebas |

**Falsas alarmas descartadas (no son defectos):**
- Errores `not_found: no queue 'orders.inventory.queue'` en el log del broker a las 16:50 UTC: provienen del arranque inicial de la sesión anterior, cuando `notification-service` consultó las colas antes de que `order-service` (en reinicio por un error ya corregido) las declarara. No se repiten.
- Texto `verificaciÃ³n` en la consola de PowerShell: es la decodificación de la consola; la interfaz React muestra `verificación` correctamente.

---

## 8. Archivos modificados o creados

**Modificados en esta ejecución final**

| Archivo | Cambio |
|---|---|
| `backend/order-service/src/main/java/com/cloudorders/orders/config/RabbitTopologyConfig.java` | Beans explícitos de Exchange/Queue/Binding + Javadoc de rutas |
| `backend/order-service/src/main/java/com/cloudorders/orders/config/RabbitTopologyProperties.java` | `Map` → registro `Queues(created, audit, inventory)` |
| `backend/notification-service/.../processing/ManualAckEventProcessor.java` | Javadoc de la política de errores |
| `backend/rabbitmq-admin-service/.../controller/AdminController.java` | Javadoc de los endpoints |
| `README.md` | Beans explícitos, conteo de pruebas, nota del nombre del clúster, enlace a esta auditoría |

**Creados en esta ejecución final**

| Archivo | Propósito |
|---|---|
| `backend/order-service/src/test/java/com/cloudorders/orders/RabbitTopologyConfigTests.java` | 3 pruebas de la topología |
| `backend/notification-service/.../consumers/notification/OrderNotificationListener.java` y `FailureSimulator.java` | Dominio notificaciones |
| `backend/notification-service/.../consumers/audit/OrderAuditListener.java` | Dominio auditoría |
| `backend/notification-service/.../consumers/inventory/OrderInventoryListener.java` | Dominio inventario |
| `AUDITORIA_FINAL_EP3_CLOUDORDERS.md` | Este informe |

**Eliminados (archivos propios, reemplazados por los de `consumers/`):** `notification-service/.../listener/*` (3 archivos) y `processing/FailureSimulator.java`.

**Implementación RabbitMQ previa (sin commitear; visible con `git status`):** `.gitignore`, `.env.example`, `docker-compose.yml`, `infra/rabbitmq/cluster.conf`, `backend/pom.xml`, `backend/Dockerfile`, `backend/.dockerignore`, módulos `messaging-contract`, `notification-service` y `rabbitmq-admin-service`, y en `order-service`: `pom.xml`, `application.yml`, `OrderService`, `OrderRepository`, `InMemoryOrderRepository`, `GlobalExceptionHandler`, `messaging/*`, `OrderApiTests` y `src/test/resources/application-test.yml`.

**No modificados:** todo el frontend (`CloudOrders/frontend`). Los endpoints existentes del backend conservan su contrato.

**Fuera del repositorio:** archivos temporales de extracción del PDF en `%TEMP%\pdfx` (no versionados). El archivo `.env` local con secretos aleatorios está ignorado por git; un escaneo de los archivos versionables no encontró ninguno de sus valores.

---

## 9. Limitaciones y riesgos pendientes

**Del PDF oficial, fuera de los 8 indicadores y fuera del alcance de esta ejecución (siguen pendientes):**
- El PDF indica que el frontend debe ser un componente **Angular**; el proyecto usa **React + Vite**. El documento de diseño afirma que fue autorizado por el docente, pero **esa autorización no pudo comprobarse** desde el repositorio. **[NO COMPROBADO]**
- **Base de datos cloud** con entidades, repositorios y propiedades de conexión: no implementada (persistencia en memoria).
- **JWT / IDaaS**: filtros en el backend y flujo de login en el frontend: no implementados (excluidos expresamente en esta ejecución).
- Entrega por GitHub (enlaces en AVA y correo): no realizada; no se hizo ningún commit ni push.

**Técnicas:**
1. **Sin persistencia ni outbox.** Guardar el pedido y publicar el evento no es atómico. Si el broker confirma tarde o falla tras recibir el mensaje, puede quedar un evento de un pedido revertido. Los IDs de pedidos revertidos no se reutilizan y todos los pedidos se pierden al reiniciar `order-service` (en esta sesión se reinició varias veces: el listado actual tiene 1 pedido).
2. **Un clúster de 2 nodos no tolera la caída de uno** con colas quorum (necesitan mayoría): se observó que sin un nodo las publicaciones no se confirman y la API responde 503. Tolerar fallos exige 3 nodos. **[VERIFICADO en la sesión anterior]**
3. La deduplicación de `eventId` es en memoria y se pierde al reiniciar el consumidor.
4. Las **DLQ no tienen consumidor ni herramienta de reproceso**; acumulan mensajes (hoy 3/5/3 de pruebas).
5. Los tres dominios (notificación, auditoría, inventario) se despliegan dentro de **un mismo servicio** (`notification-service`); están separados por paquetes, no por microservicio.
6. La API administrativa usa **HTTP Basic sin TLS** y el mismo usuario de broker que la aplicación; no incluye operación de "actualización". Para producción: TLS, usuarios con mínimo privilegio y gestor de secretos.
7. El nombre del clúster se fijó con un comando en caliente; no está en `cluster.conf`, por lo que un clúster nuevo volvería a mostrar nombres distintos (cosmético).
8. `FailureSimulator` es código de prueba dentro del servicio (desactivado por defecto con `simulation.enabled=false`; en el despliegue actual está desactivado).
9. Reserva menor en el indicador 6: `ManualAckEventProcessor` usa tipos AMQP (`Channel`, `Message`) y `GlobalExceptionHandler` del administrador mapea excepciones AMQP; es la capa de infraestructura, no los controladores ni los servicios de dominio.
10. No se compararon imágenes base con versiones fijas por *digest*; el `Dockerfile` usa etiquetas de versión mayor (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`).

---

## 10. Qué fue verificado, qué fue inferido y qué no pudo comprobarse

| Categoría | Contenido |
|---|---|
| **Verificado (ejecutado en esta sesión)** | Estado de los 5 contenedores y sus healthchecks; clúster de 2 nodos sin particiones; 6 colas quorum con miembros en ambos nodos; exchanges direct y topic; 6 bindings; 3 consumidores con ACK manual; 34/34 pruebas automáticas; `npm run build`; `GET/POST` de pedidos y contrato HTTP; `ORDER_CREATED` confirmado y 3 ACK por pedido; mensaje inválido → +1 en cada DLQ; API administrativa (401/400/403/201/204); frontend en navegador real (conexión, validación, alta, tabla); topología idéntica tras el refactor; ausencia de secretos en archivos versionables; no se eliminaron volúmenes |
| **Inferido del código** | Reintentos con fallo transitorio, fallo fatal, caídas de uno y de ambos nodos y escenarios 409/404 de la API (probados en la sesión anterior, no repetidos hoy, sin cambios de lógica); los niveles de desempeño de la columna "Nivel inferido"; ausencia de AMQP en la capa de dominio (por búsqueda de `import`) |
| **No comprobado** | Calificación oficial del docente; autorización de React en lugar de Angular; Management UI observada visualmente (solo su API); diseño del frontend en escritorio; comportamiento con 3 o más nodos; carga/concurrencia; Docker en otro equipo o desde cero (los volúmenes existentes no se tocaron, por lo que un arranque con datos vacíos no se repitió hoy); IDaaS/JWT/BD cloud (no implementados) |

---

## 11. Conclusión

La implementación RabbitMQ de CloudOrders cumple los **ocho indicadores** de la rúbrica EP3 según el código y la ejecución real verificada en esta sesión, tras corregir dos puntos concretos que el PDF penaliza. El frontend y el backend siguen funcionando con el contrato original. Antes de la entrega siguen pendientes los requisitos generales del PDF ajenos a RabbitMQ (frontend Angular o confirmación escrita de la excepción React, IDaaS/JWT, base de datos cloud) y la publicación en GitHub, que el equipo debe realizar con sus propios commits.
