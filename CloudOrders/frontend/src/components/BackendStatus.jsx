const LABELS = {
  checking: 'Verificando conexión…',
  connected: 'Backend conectado',
  disconnected: 'Backend desconectado',
  unconfigured: 'Backend no configurado',
  error: 'Backend con errores',
}

const DESCRIPTIONS = {
  checking: 'Comprobando si el backend responde.',
  connected: 'El backend respondió correctamente.',
  disconnected: 'No se pudo contactar al backend. Aún no está disponible o la URL es incorrecta.',
  unconfigured: 'Define VITE_API_BASE_URL en el archivo .env para indicar la URL del backend.',
  error: 'El backend respondió, pero con un error. Revisa las rutas acordadas.',
}

export default function BackendStatus({ status, compact = false, onRetry }) {
  if (compact) {
    return (
      <span className={`status-pill status-pill--${status}`} role="status">
        <span className="status-pill__dot" aria-hidden="true" />
        {LABELS[status]}
      </span>
    )
  }

  return (
    <section className={`card backend-card backend-card--${status}`} aria-labelledby="backend-titulo">
      <h2 id="backend-titulo">Estado del backend</h2>
      <p role="status">
        <strong>{LABELS[status]}</strong>
        <br />
        {DESCRIPTIONS[status]}
      </p>
      {onRetry && (
        <button type="button" className="btn btn--secondary" onClick={onRetry}>
          Reintentar conexión
        </button>
      )}
    </section>
  )
}
