# Evidencias EP4 — CloudOrders

Resultados **reales** obtenidos el 22-10-2026 en el entorno local. No contienen tokens ni secretos
(solo IDs públicos de tenant/aplicación y nombres de usuario visibles en pantalla).

## Capturas disponibles

| Archivo | Muestra |
|---|---|
| `02-login-entra-id.png` | Pantalla real de inicio de sesión de Microsoft Entra ID abierta desde React |
| `03-react-usuario-autenticado.png` | React con el usuario autenticado (Entra ID), «Backend conectado» |
| `14a-pedido-creado-react-local.png` | Pedido creado desde el formulario React autenticado y listado desde PostgreSQL (entorno **local**) |

## Capturas pendientes (requieren la infraestructura cloud)

1 página principal desplegada · 4 JWT aceptado por el gateway · 5 petición rechazada por el gateway · 6–7 API Gateway y rutas ·
8 frontend y backend desplegados · 9 PostgreSQL cloud · 10 `docker compose` en la VM · 11–13 RabbitMQ Overview, Queues y Exchanges ·
14 pedido de extremo a extremo en cloud.

## Registro de pruebas locales (Entra ID real)

| Prueba | Resultado |
|---|---|
| Tenant `72fd0b5a-8a6a-4cff-89f6-bde961f7e250` (Duoc UC); apps «CloudOrders API» y «CloudOrders SPA» creadas con `infra/entra/setup-entra.ps1` | OK |
| API: tokens v2, scopes `orders.read` y `orders.write`, SPA preautorizada | OK (verificado por Graph) |
| Login React → Entra ID con Authorization Code + PKCE (`response_type=code`, `code_challenge_method=S256`) | OK (usuario real, con MFA si la cuenta lo exige) |
| Access token real: `iss` = `https://login.microsoftonline.com/<tenant>/v2.0`, `aud` = client ID de la API, `scp` = `orders.read orders.write`, `ver` 2.0 | OK |
| `GET /api/orders` sin token | 401 |
| `GET /api/orders` con token inválido (`abc.def.ghi`) | 401 |
| `GET /api/orders` con token real con la firma alterada | 401 |
| `GET /api/orders` con token real válido | 200 |
| `POST /api/orders` con token real válido | 201; fila en PostgreSQL |
| Evento `ORDER_CREATED` publicado y confirmado; 3 consumidores (`notification`, `inventory`, `audit`) con ACK en el primer intento (pedidos 2 y 3) | OK |
| Pedido persiste tras `docker restart` de `order-service` (consultado por la API con token real) | OK |
| Pedido #3 creado desde el formulario de React autenticado | OK |

## Notas

- El usuario de prueba es la cuenta institucional de quien ejecutó el login; **no se crearon usuarios** en el tenant de Duoc UC. Si la rúbrica exige mostrar «usuarios registrados», usar *Entra admin center → Users* o crear un usuario de prueba con `setup-entra.ps1` (sin `-SkipTestUser`) en un tenant propio.
- Para probar con tokens reales sin navegador se preautorizó temporalmente el cliente de Azure CLI (`04b07795-8ddb-461a-bbee-02f9e1bf7b46`) sobre la API: **retirar al terminar** (Entra admin center → CloudOrders API → Expose an API → Authorized client applications).
- Pendiente: las pruebas contra API Gateway y todo lo cloud (ver `docs/ARQUITECTURA_CLOUD.md`).
