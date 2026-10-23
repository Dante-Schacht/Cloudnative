# CloudOrders

Proyecto desarrollado para la asignatura **Desarrollo Cloud Native I (DSY1107)** de Duoc UC.

CloudOrders es una aplicación web que permite registrar y consultar pedidos, integrando microservicios, autenticación y mensajería asíncrona.

## Tecnologías utilizadas

- **Frontend:** React + Vite
- **Backend:** Java 21 + Spring Boot
- **Base de datos:** PostgreSQL
- **Mensajería:** RabbitMQ
- **Autenticación:** Microsoft Entra ID + JWT
- **Contenedores:** Docker + Docker Compose

## Funcionalidades

- Inicio de sesión institucional.
- Registro y consulta de pedidos.
- Almacenamiento persistente en PostgreSQL.
- Procesamiento asíncrono con RabbitMQ.
- Tres consumidores: notificación, auditoría e inventario.
- Manejo de errores mediante ACK, NACK y DLQ.
- Clúster RabbitMQ de dos nodos.

## Ejecución local

Con Docker instalado y las variables de entorno configuradas:

```bash
docker compose up -d --build
```

Para iniciar el frontend:

```bash
cd frontend
npm install
npm run dev
```

**Frontend:** http://localhost:5173

## Integrantes

- Dante Schacht
- Natanael Reveco

**Asignatura:** Desarrollo Cloud Native I — Duoc UC.
