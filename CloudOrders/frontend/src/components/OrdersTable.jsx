const STATUS_LABELS = {
  PENDING: 'Pendiente',
  PROCESSING: 'Procesando',
  COMPLETED: 'Completado',
  FAILED: 'Fallido',
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('es-CL')
}

export default function OrdersTable({ orders, loading, error }) {
  let message = null
  if (loading) message = 'Cargando pedidos…'
  else if (error) message = error
  else if (orders.length === 0) message = 'No hay pedidos para mostrar.'

  return (
    <section className="card" aria-labelledby="tabla-titulo">
      <h2 id="tabla-titulo">Listado de pedidos</h2>
      <div className="table-wrap">
        <table className="table">
          <caption className="sr-only">Pedidos registrados</caption>
          <thead>
            <tr>
              <th scope="col">ID</th>
              <th scope="col">Cliente</th>
              <th scope="col">Producto</th>
              <th scope="col">Cantidad</th>
              <th scope="col">Fecha</th>
              <th scope="col">Estado</th>
            </tr>
          </thead>
          <tbody>
            {message ? (
              <tr>
                <td colSpan="6" className="table__empty">{message}</td>
              </tr>
            ) : (
              orders.map((order) => {
                const known = order.status in STATUS_LABELS
                const label = known ? STATUS_LABELS[order.status] : 'Desconocido'
                return (
                  <tr key={order.id}>
                    <td>{order.id}</td>
                    <td>{order.customerName}</td>
                    <td>
                      {order.product}
                      {order.notes && <small className="table__notes">{order.notes}</small>}
                    </td>
                    <td>{order.quantity}</td>
                    <td>{formatDate(order.createdAt)}</td>
                    <td>
                      <span className={`badge badge--${known ? order.status.toLowerCase() : 'unknown'}`}>{label}</span>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}
