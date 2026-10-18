# CloudOrders – Frontend

Frontend de CloudOrders (Desarrollo Cloud Native I, Duoc UC). React 19 + Vite, JavaScript.

## Requisitos
- Node.js `^20.19.0` o `>=22.12.0` (requerido por Vite 8)
- npm

## Uso
```bash
npm install
cp .env.example .env   # en Windows: copy .env.example .env
npm run dev            # desarrollo
npm run build          # compilación a dist/
npm run preview        # previsualizar la compilación
```

## Configuración
`VITE_API_BASE_URL` define la URL del backend Spring Boot (ej. `http://localhost:8080`). No guardar secretos en variables `VITE_*`: quedan expuestas en el navegador.

## Estructura
```
src/
  components/  Componentes reutilizables (Header, OrderForm, OrdersTable, ...)
  pages/       Páginas (Inicio, Pedidos)
  services/    api.js: acceso al backend con fetch
  styles/      Estilos globales
```

## Integración con el backend (pendiente)
`src/services/api.js` centraliza el contrato provisional:
- `GET  /api/health` – estado del backend (200 si está operativo)
- `GET  /api/orders` – lista de pedidos con `id`, `customerName`, `product`, `quantity`, `notes`, `status`, `createdAt`
- `POST /api/orders` – crea un pedido con `customerName`, `product`, `quantity`, `notes`

Estados: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.

**Estas rutas y los campos del pedido deben coordinarse con el backend Spring Boot antes de usarlas.** Los estados esperados son pendiente, procesando, completado y fallido. El backend deberá habilitar CORS para el origen del frontend.

La aplicación no muestra datos ficticios: sin backend, indica "Backend desconectado" y la tabla queda vacía.

## Fuera de alcance de esta etapa
Backend Spring Boot, RabbitMQ, Docker, JWT, Microsoft Entra ID, AWS API Gateway y base de datos.
