import BackendStatus from '../components/BackendStatus.jsx'

export default function HomePage({ backend, onRetry }) {
  return (
    <>
      <section className="hero">
        <h1>Gestión de pedidos en la nube</h1>
        <p>
          CloudOrders permite registrar y hacer seguimiento de pedidos. Esta es la primera
          etapa: la interfaz está lista para integrarse con microservicios Spring Boot y
          mensajería con RabbitMQ.
        </p>
        <a className="btn" href="#/pedidos">Ir a pedidos</a>
      </section>
      <BackendStatus status={backend.status} onRetry={onRetry} />
    </>
  )
}
