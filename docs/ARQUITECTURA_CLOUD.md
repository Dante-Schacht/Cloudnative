# CloudOrders — Arquitectura cloud, costos y plan de despliegue

> Estado: **propuesta preparada localmente. No se ha creado ningún recurso en la nube.**
> Las cifras de costo son **estimaciones de orden de magnitud** (no se consultaron precios en vivo):
> confírmalas en la [calculadora de precios de Google Cloud](https://cloud.google.com/products/calculator) antes de autorizar.

## 1. Arquitectura objetivo

```
Usuario ─► Frontend React (Cloud Run, nginx)
              │  1. Login OIDC (Authorization Code + PKCE, MSAL)
              ▼
        Microsoft Entra ID ──► access token JWT (aud = API, scp = orders.read/orders.write)
              │  2. fetch + Authorization: Bearer <JWT>
              ▼
   Google Cloud API Gateway  (valida firma/issuer/audience/exp con el JWKS de Entra; 401 si falla)
              │  3. ID token de Google + X-Forwarded-Authorization (token original)
              ▼
   order-service (Cloud Run, privado) ── valida el JWT otra vez (Spring Security) y el scope
        │                      │
        │ JDBC (TLS, IP privada)│ AMQP (VPC, IP interna)
        ▼                      ▼
   Cloud SQL PostgreSQL     VM Compute Engine (Docker Compose)
                              ├─ rabbitmq1 + rabbitmq2 (clúster, colas quorum, DLQ)
                              ├─ notification-service (3 consumidores: notificación, auditoría, inventario)
                              └─ rabbitmq-admin-service (solo 127.0.0.1)
```

| Componente | Servicio propuesto | Archivos del repositorio |
|---|---|---|
| Frontend | Cloud Run (nginx sin privilegios) | `frontend/Dockerfile`, `frontend/nginx.conf` |
| API Manager | Google Cloud API Gateway | `infra/gcp/openapi-gateway.yaml.tpl` |
| Backend de pedidos | Cloud Run (privado, solo invocable por el gateway) | `backend/Dockerfile`, `application-cloud.yml` |
| Base de datos | Cloud SQL for PostgreSQL | `V1__create_orders.sql` (Flyway) |
| Mensajería | VM + Docker Compose (RabbitMQ 2 nodos) | `infra/gcp/docker-compose.vm.yml` |
| IDaaS | Microsoft Entra ID | sección 4 |

### Desviación justificada respecto de "todo en Cloud Run"
`notification-service` y `rabbitmq-admin-service` **no** van a Cloud Run: los consumidores AMQP mantienen una
conexión permanente y Cloud Run reduce la CPU entre peticiones (o factura una instancia siempre activa). En la VM
comparten red con RabbitMQ, lo que además cumple el requisito de mostrar *docker-compose* levantando RabbitMQ en la nube.

## 2. Compatibilidad y puntos de atención

| Tema | Detalle |
|---|---|
| **Región** | Usar `us-central1` para todo. API Gateway solo está disponible en un subconjunto de regiones (verificar con `gcloud api-gateway gateways create --help` / documentación) y mantener Cloud SQL, Cloud Run y la VM en la misma región evita latencia y costo de tráfico. Desde Chile la latencia ronda los 150 ms; es aceptable para la demostración. |
| **API Gateway usa OpenAPI 2.0** | Por eso la especificación es `swagger: "2.0"`. |
| **JWT de Entra en API Gateway** | `x-google-issuer`, `x-google-jwks_uri` y `x-google-audiences` aceptan cualquier emisor OIDC. Con tokens **v2** el emisor es `https://login.microsoftonline.com/{tenant}/v2.0` y `aud` es el **client ID (GUID) de la API**. Hay que fijar `requestedAccessTokenVersion = 2` en el manifiesto de la API; con v1 el emisor es `sts.windows.net` y `aud` es `api://…`, y la validación fallaría. |
| **Authorization reemplazado** | Con `x-google-backend` (y `jwt_audience`), API Gateway sustituye `Authorization` por un ID token de Google y copia el original a `X-Forwarded-Authorization`. **Confirmado en la documentación oficial** ([openapi-extensions](https://cloud.google.com/endpoints/docs/openapi/openapi-extensions) y [Auth0/JWT en API Gateway](https://cloud.google.com/api-gateway/docs/authenticating-users-auth0)): el backend debe verificar el JWT de esa cabecera, que es lo que hace `JwtValidationConfig#bearerTokenResolver`. Falta confirmarlo en el primer despliegue real. |
| **Evitar el gateway** | `order-service` en Cloud Run se despliega con `--no-allow-unauthenticated` y solo la cuenta de servicio del gateway tiene `roles/run.invoker`: una llamada directa a la URL `run.app` responde 403. Aun así el backend valida el JWT de Entra por sí mismo (defensa en profundidad). |
| **CORS** | API Gateway no responde CORS: la especificación define `OPTIONS /api/orders` sin seguridad y `order-service` genera las cabeceras. Debe incluir el origen del frontend desplegado (`CLOUDORDERS_CORS_ALLOWED_ORIGINS`). |
| **Cloud Run → VM** | *Direct VPC egress* (sin conector con costo fijo). Firewall: AMQP 5672/5673 solo desde el rango de la subred de Cloud Run. |
| **Cloud Run → Cloud SQL** | IP privada (acceso privado a servicios) + `sslmode=require`. Alternativa: socket factory de Cloud SQL. |
| **VM sin puertos públicos** | Sin reglas de entrada desde Internet; SSH por IAP (`35.235.240.0/20`). Management UI y API de administración por túnel SSH. |
| **Clúster de 2 nodos** | Las colas quorum necesitan mayoría: con 2 nodos, **si cae uno no hay quórum** y las publicaciones dejan de confirmarse (la API responde 503). Tolerar la caída de un nodo exigiría 3 nodos. Además, los dos nodos en una sola VM no aportan alta disponibilidad real: cumplen el requisito académico de "dos nodos integrados en un clúster". |
| **IP externa de la VM** | Se necesita salida a Internet para descargar imágenes Docker. Una IP externa efímera con firewall cerrado es más barata que Cloud NAT. |

## 3. Costos estimados (orden de magnitud, USD)

| Recurso | Opción A: todo en GCP | Opción B: más económica |
|---|---|---|
| Cloud Run (frontend + order-service, `min-instances=0`) | ≈ 0 (cae en la capa gratuita con tráfico de demostración) | ≈ 0 |
| API Gateway | ≈ 0 con poco tráfico (se cobra por millón de llamadas, con capa gratuita) | ≈ 0 |
| Cloud SQL PostgreSQL `db-f1-micro` + 10 GB | ≈ 8–12 / mes (≈ 0,3–0,4 / día encendida) | PostgreSQL gestionado gratuito de terceros (p. ej. Neon o Supabase, plan gratuito): ≈ 0 |
| VM `e2-medium` (4 GB) + disco 20 GB + IP externa | ≈ 28–35 / mes | `e2-small` (2 GB) con límites de memoria: ≈ 15–20 / mes |
| Artifact Registry, Secret Manager, tráfico | ≈ 1–3 / mes | ≈ 1–3 / mes |
| Entra ID (tenant gratuito, usuarios de prueba) | 0 | 0 |
| **Total aproximado** | **≈ 40–50 / mes**, o **≈ 3–5** si solo existe 3–4 días y se elimina | **≈ 17–25 / mes**, o **≈ 2–3** si se elimina tras la demo |

Recomendación académica: **Opción A creada pocos días antes de la presentación y eliminada después** (la rúbrica
pide "base de datos cloud" y Cloud SQL es lo más defendible). La Opción B con una BD gratuita de terceros es válida
si el presupuesto es el límite; cambia solo `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` (exigir TLS: `sslmode=require`).
Las cuentas nuevas de Google Cloud pueden tener crédito de prueba (si aplica): requiere habilitar facturación, lo
que debe autorizar el titular.

## 4. Microsoft Entra ID: qué registrar

Requiere un **tenant** (el gratuito de Entra ID sirve) y permisos para registrar aplicaciones. Nada de esto se ha
creado. Todo se hace en *Microsoft Entra admin center → Identity → Applications → App registrations*.

### 4.1 App "CloudOrders API" (recurso protegido)
1. *New registration* → "CloudOrders API", cuentas: **solo este directorio**. Sin redirect URI.
2. *Expose an API* → Application ID URI `api://<client-id-de-la-API>` → agregar scopes **delegados**:
   `orders.read` y `orders.write` (consentimiento: administradores y usuarios).
3. *Manifest* → `api.requestedAccessTokenVersion = 2`.
4. Valores que se usan después: **Directory (tenant) ID** → `ENTRA_TENANT_ID`; **Application (client) ID de la API** → `ENTRA_API_AUDIENCE`.

### 4.2 App "CloudOrders SPA" (cliente público)
1. *New registration* → "CloudOrders SPA", solo este directorio.
2. *Authentication* → plataforma **Single-page application** (esto habilita Authorization Code + PKCE; **no** crear client secret).
   Redirect URIs: `http://localhost:5173/` y la URL del frontend desplegado (con `/` final).
3. *API permissions* → agregar **My APIs → CloudOrders API** → delegados `orders.read` y `orders.write` (y *Grant admin consent* si corresponde).
4. Valores: **client ID de la SPA** → `VITE_ENTRA_CLIENT_ID`; tenant → `VITE_ENTRA_TENANT_ID`;
   `VITE_ENTRA_API_SCOPES=api://<client-id-de-la-API>/orders.read,api://<client-id-de-la-API>/orders.write`.

### 4.3 Usuarios de prueba
*Users → New user* en el tenant (2–3 cuentas de prueba, por ejemplo una con ambos scopes y otra sin consentimiento
de `orders.write`). Para evidenciar el rechazo por permisos basta consentir solo `orders.read` en una de ellas.

### 4.4 Autenticación vs. autorización en el backend
- **Autenticación** (`JwtValidationConfig`): firma (JWKS de Entra), issuer, expiración y audience.
- **Autorización** (`SecurityConfig`): `GET /api/orders` exige `SCOPE_orders.read`; `POST /api/orders` exige `SCOPE_orders.write`;
  `/api/health` es público (healthchecks de Docker y de Cloud Run).
- Sin `ENTRA_TENANT_ID`/`ENTRA_API_AUDIENCE` el servicio arranca pero responde 401 a todo lo protegido (falla cerrada).

## 5. Secuencia de despliegue (NO ejecutada; requiere tu autorización)

Sustituye los valores `<...>`. Verifica los flags con `gcloud <comando> --help`, porque cambian entre versiones.
No pegues contraseñas en la consola: se leen desde Secret Manager.

```powershell
# 0. Proyecto y APIs (requiere facturación habilitada por el titular)
gcloud auth login
gcloud config set project <PROJECT_ID>
gcloud services enable run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com `
  artifactregistry.googleapis.com apigateway.googleapis.com servicemanagement.googleapis.com `
  servicecontrol.googleapis.com compute.googleapis.com servicenetworking.googleapis.com

# 1. Red: VPC propia, subred y reglas (sin entrada desde Internet salvo SSH por IAP)
gcloud compute networks create cloudorders-vpc --subnet-mode=custom
gcloud compute networks subnets create cloudorders-subnet --network=cloudorders-vpc --region=us-central1 --range=10.10.0.0/24
gcloud compute firewall-rules create allow-iap-ssh --network=cloudorders-vpc --allow=tcp:22 --source-ranges=35.235.240.0/20 --target-tags=rabbitmq-vm
gcloud compute firewall-rules create allow-amqp-from-subnet --network=cloudorders-vpc --allow=tcp:5672,tcp:5673 --source-ranges=10.10.0.0/24 --target-tags=rabbitmq-vm
gcloud compute addresses create google-managed-services --global --purpose=VPC_PEERING --prefix-length=16 --network=cloudorders-vpc
gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com --ranges=google-managed-services --network=cloudorders-vpc

# 2. Cloud SQL (IP privada, TLS obligatorio); crear la contraseña directamente en Secret Manager
gcloud sql instances create cloudorders-pg --database-version=POSTGRES_16 --edition=ENTERPRISE --tier=db-f1-micro `
  --region=us-central1 --network=cloudorders-vpc --no-assign-ip --ssl-mode=ENCRYPTED_ONLY --storage-size=10
gcloud sql databases create cloudorders --instance=cloudorders-pg
gcloud sql users create cloudorders --instance=cloudorders-pg --password=<LEER_DESDE_SECRET_MANAGER>

# 3. VM con Docker + Compose (clonar el repo y crear el .env de la VM con VM_INTERNAL_IP y secretos propios)
gcloud compute instances create rabbitmq-vm --zone=us-central1-a --machine-type=e2-medium `
  --subnet=cloudorders-subnet --tags=rabbitmq-vm --image-family=debian-12 --image-project=debian-cloud --boot-disk-size=20GB
gcloud compute ssh rabbitmq-vm --zone=us-central1-a --tunnel-through-iap
#   en la VM: instalar docker, clonar el repo y ejecutar
#   docker compose --env-file .env -f infra/gcp/docker-compose.vm.yml up -d --build

# 4. Imágenes (construidas localmente) en Artifact Registry
gcloud artifacts repositories create cloudorders --repository-format=docker --location=us-central1
gcloud auth configure-docker us-central1-docker.pkg.dev
docker build --build-arg SERVICE=order-service -t us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/order-service:1 backend
docker push us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/order-service:1

# 5. order-service en Cloud Run (privado)
gcloud run deploy order-service --image us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/order-service:1 `
  --region us-central1 --no-allow-unauthenticated --network cloudorders-vpc --subnet cloudorders-subnet `
  --vpc-egress private-ranges-only --min-instances 0 --max-instances 2 `
  --set-env-vars "^@^SPRING_PROFILES_ACTIVE=cloud@DB_URL=jdbc:postgresql://<IP_PRIVADA_SQL>:5432/cloudorders?sslmode=require@DB_USERNAME=cloudorders@RABBITMQ_ADDRESSES=<VM_IP>:5672,<VM_IP>:5673@ENTRA_TENANT_ID=<TENANT>@ENTRA_API_AUDIENCE=<API_CLIENT_ID>@CLOUDORDERS_CORS_ALLOWED_ORIGINS=<URL_FRONTEND>" `
  --set-secrets "DB_PASSWORD=cloudorders-db-password:latest,RABBITMQ_USERNAME=cloudorders-rabbit-user:latest,RABBITMQ_PASSWORD=cloudorders-rabbit-password:latest"
#   (el prefijo ^@^ cambia el separador de gcloud a "@" para poder usar comas en RABBITMQ_ADDRESSES)

# 6. API Gateway: renderizar la especificación y crear API, config y gateway
gcloud iam service-accounts create cloudorders-gateway
gcloud run services add-iam-policy-binding order-service --region us-central1 `
  --member serviceAccount:cloudorders-gateway@<PROJECT_ID>.iam.gserviceaccount.com --role roles/run.invoker
(Get-Content infra/gcp/openapi-gateway.yaml.tpl -Raw) `
  -replace '__ORDER_SERVICE_URL__','<URL_CLOUD_RUN>' -replace '__ENTRA_TENANT_ID__','<TENANT>' `
  -replace '__ENTRA_API_AUDIENCE__','<API_CLIENT_ID>' | Set-Content openapi-rendered.yaml   # no versionar
gcloud api-gateway apis create cloudorders-api
gcloud api-gateway api-configs create cfg-1 --api=cloudorders-api --openapi-spec=openapi-rendered.yaml `
  --backend-auth-service-account=cloudorders-gateway@<PROJECT_ID>.iam.gserviceaccount.com
gcloud api-gateway gateways create cloudorders-gw --api=cloudorders-api --api-config=cfg-1 --location=us-central1

# 7. Frontend con la URL del gateway (no localhost) y registro del redirect URI en Entra
docker build -t us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/frontend:1 `
  --build-arg VITE_API_BASE_URL=https://<GATEWAY_HOST> --build-arg VITE_ENTRA_CLIENT_ID=<SPA_CLIENT_ID> `
  --build-arg VITE_ENTRA_TENANT_ID=<TENANT> --build-arg "VITE_ENTRA_API_SCOPES=<scopes>" frontend
docker push us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/frontend:1
gcloud run deploy frontend --image us-central1-docker.pkg.dev/<PROJECT_ID>/cloudorders/frontend:1 --region us-central1 --allow-unauthenticated
#   luego: añadir la URL del frontend como redirect URI SPA en Entra y en CLOUDORDERS_CORS_ALLOWED_ORIGINS de order-service
```

Orden obligatorio: VPC → Cloud SQL → VM/RabbitMQ → order-service → gateway → frontend → ajuste de CORS y redirect URI.
Eliminación posterior (también requiere autorización): `gateways`, `api-configs`, `apis`, servicios Cloud Run, VM, instancia Cloud SQL, VPC y secretos.

## 6. Consistencia PostgreSQL ↔ RabbitMQ: propuesta de *Transactional Outbox* (NO implementada)

**Situación actual** (`OrderService#create`): se guarda el pedido (commit inmediato), se publica `ORDER_CREATED` con
*publisher confirms* y, si el broker no confirma, se borra el pedido (compensación) y se responde 503.

**Riesgos residuales**
1. El proceso cae entre el commit y la publicación: pedido guardado sin evento (nadie lo notifica).
2. La compensación (`deleteById`) falla o el broker confirma tarde: queda un evento de un pedido inexistente.
3. La API depende de que RabbitMQ esté disponible en el instante de crear el pedido (con 2 nodos, perder uno ya produce 503).

**Propuesta**
- Tabla `outbox_events(id uuid, aggregate_id, event_type, payload jsonb, created_at, published_at null, attempts)` (migración `V2`).
- `OrderService#create` guarda el pedido y la fila del outbox **en la misma transacción** y responde 201 sin tocar RabbitMQ.
- Un *relay* (`@Scheduled`, `SELECT … FOR UPDATE SKIP LOCKED`) publica los eventos pendientes reutilizando `RabbitOrderEventPublisher`
  (con confirms) y marca `published_at`. Entrega *at-least-once*; los consumidores ya deduplican por `eventId`
  (conviene persistir esa deduplicación, hoy es en memoria).
- Sin cambios en `RabbitTopologyConfig`, en el contrato `OrderCreatedEvent` ni en los consumidores.

**Costos / decisiones para ti**: cambia el comportamiento observable (ya no hay 503 por broker caído; el evento llega con
retraso) y obliga a actualizar `OrderApiTests`. Recomendación: **primero completar la demostración** con el mecanismo actual
y, solo si hay tiempo, implementar el outbox. **Decisión pendiente tuya.**

## 7. Plan de recursos para autorización (consulta de precios del 22-10-2026)

Fuentes: página oficial de precios de API Gateway (2 M de llamadas/mes gratis, luego US$3 por millón) y resúmenes de precios de Cloud Run, Cloud SQL y Compute Engine
obtenidos por búsqueda web (no desde la calculadora oficial: **confirmar en https://cloud.google.com/products/calculator** antes de aprobar).

| Recurso a crear (un solo proyecto nuevo, región `us-central1`) | Facturable | Costo aprox. |
|---|---|---|
| Cloud Run `order-service` y `frontend` (`min-instances=0`) | Gratis dentro de 2 M solicitudes/mes | ≈ 0 |
| API Gateway (1 API, 1 config, 1 gateway) | Gratis hasta 2 M llamadas/mes | ≈ 0 |
| Cloud SQL PostgreSQL 16 `db-f1-micro`, 10 GB, IP privada | Sí | ≈ US$0,015/h (≈ US$11/mes) + almacenamiento |
| VM `e2-medium` (4 GB: 2 nodos RabbitMQ + 2 servicios Java), disco 20 GB, IP externa efímera | Sí | ≈ US$0,034/h (≈ US$24,5/mes) + disco/IP ≈ US$3 |
| Artifact Registry, Secret Manager, VPC peering | Ínfimo | ≈ US$1 |

Con los recursos encendidos **≈ US$2,5 por día** (≈ US$0,06/h entre Cloud SQL y la VM). Para 3–4 días de preparación y demostración: **≈ US$8–12 en total**, si
después se eliminan. `e2-small` (2 GB) no se recomienda: RabbitMQ x2 y dos JVM no caben con holgura.
Requisitos previos: cuenta de Google con **facturación habilitada** (crédito de prueba si aplica) y `gcloud` instalado; no se ha hecho ninguna de las dos cosas.
