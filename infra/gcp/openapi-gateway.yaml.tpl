swagger: "2.0"
info:
  title: CloudOrders API
  description: API de pedidos de CloudOrders expuesta mediante Google Cloud API Gateway.
  version: "1.0.0"
schemes:
  - https
produces:
  - application/json

# Backend: order-service en Cloud Run (privado). Con jwt_audience, API Gateway llama al servicio con su
# propio ID token de Google y deja el token original de Entra ID en X-Forwarded-Authorization
# (order-service lo lee primero; ver JwtValidationConfig).
x-google-backend:
  address: __ORDER_SERVICE_URL__
  path_translation: APPEND_PATH_TO_ADDRESS
  jwt_audience: __ORDER_SERVICE_URL__

securityDefinitions:
  entra_id:
    authorizationUrl: ""
    flow: implicit
    type: oauth2
    x-google-issuer: "https://login.microsoftonline.com/__ENTRA_TENANT_ID__/v2.0"
    x-google-jwks_uri: "https://login.microsoftonline.com/__ENTRA_TENANT_ID__/discovery/v2.0/keys"
    x-google-audiences: "__ENTRA_API_AUDIENCE__"

paths:
  /api/health:
    get:
      operationId: health
      summary: Estado del backend (público)
      security: []
      responses:
        "200":
          description: Backend operativo.
          schema:
            $ref: "#/definitions/Health"
  /api/orders:
    get:
      operationId: listOrders
      summary: Lista los pedidos
      security:
        - entra_id: []
      responses:
        "200":
          description: Lista de pedidos.
          schema:
            type: array
            items:
              $ref: "#/definitions/Order"
        "401":
          description: Token ausente o inválido (rechazado por API Gateway).
    post:
      operationId: createOrder
      summary: Crea un pedido y publica ORDER_CREATED en RabbitMQ
      security:
        - entra_id: []
      consumes:
        - application/json
      parameters:
        - in: body
          name: order
          required: true
          schema:
            $ref: "#/definitions/CreateOrder"
      responses:
        "201":
          description: Pedido creado.
          schema:
            $ref: "#/definitions/Order"
        "400":
          description: Datos inválidos.
        "401":
          description: Token ausente o inválido (rechazado por API Gateway).
        "403":
          description: El token no incluye el scope requerido.
        "503":
          description: El broker no confirmó el evento; el pedido no fue registrado.
    options:
      # Preflight CORS: el navegador no envía Authorization, por eso no exige seguridad;
      # la respuesta (cabeceras CORS) la genera order-service.
      operationId: ordersCors
      summary: Preflight CORS
      security: []
      responses:
        "200":
          description: Preflight aceptado.

definitions:
  Health:
    type: object
    properties:
      status:
        type: string
      service:
        type: string
      timestamp:
        type: string
        format: date-time
  CreateOrder:
    type: object
    required: [customerName, product, quantity]
    properties:
      customerName:
        type: string
        maxLength: 120
      product:
        type: string
        maxLength: 120
      quantity:
        type: integer
        minimum: 1
      notes:
        type: string
        maxLength: 500
  Order:
    type: object
    properties:
      id:
        type: integer
        format: int64
      customerName:
        type: string
      product:
        type: string
      quantity:
        type: integer
      notes:
        type: string
      status:
        type: string
        enum: [PENDING, PROCESSING, COMPLETED, FAILED]
      createdAt:
        type: string
        format: date-time
