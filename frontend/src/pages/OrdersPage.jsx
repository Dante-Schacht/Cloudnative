import { useCallback, useEffect, useState } from 'react'
import OrderForm from '../components/OrderForm.jsx'
import OrdersTable from '../components/OrdersTable.jsx'
import { createOrder, listOrders } from '../services/api.js'
import { getAccount, login } from '../auth/auth.js'
import { isAuthConfigured } from '../auth/authConfig.js'

const BACKEND_MESSAGES = {
  checking: 'Verificando conexión con el backend.',
  disconnected: 'Backend desconectado.',
  unconfigured: 'Backend no configurado (falta VITE_API_BASE_URL).',
  error: 'El backend respondió con un error.',
}

export default function OrdersPage({ backend, onRetry }) {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const connected = backend.status === 'connected'
  const needsLogin = isAuthConfigured() && !getAccount()
  const canUseApi = connected && !needsLogin

  const loadOrders = useCallback(async (signal) => {
    setLoading(true)
    setError(null)
    try {
      const data = await listOrders(signal)
      setOrders(Array.isArray(data) ? data : [])
    } catch (err) {
      if (err.name === 'AbortError') return
      setOrders([])
      setError(`No se pudieron cargar los pedidos. ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!connected) {
      setOrders([])
      setLoading(false)
      setError(
        backend.status === 'checking'
          ? 'Verificando conexión con el backend…'
          : 'Sin conexión con el backend: no hay pedidos que mostrar.',
      )
      return undefined
    }
    if (needsLogin) {
      setOrders([])
      setLoading(false)
      setError('Inicia sesión para ver los pedidos.')
      return undefined
    }
    const controller = new AbortController()
    loadOrders(controller.signal)
    return () => controller.abort()
  }, [connected, needsLogin, backend.status, loadOrders])

  const handleCreate = async (order) => {
    try {
      await createOrder(order)
    } catch (err) {
      onRetry()
      throw err
    }
    await loadOrders()
  }

  return (
    <>
      <h1>Pedidos</h1>
      {!connected && (
        <p className="notice notice--warning" role="status">
          {BACKEND_MESSAGES[backend.status] ?? 'Backend no disponible.'}{' '}
          <button type="button" className="link-button" onClick={onRetry}>Reintentar</button>
        </p>
      )}
      {connected && needsLogin && (
        <p className="notice notice--warning" role="status">
          Debes iniciar sesión con tu cuenta de Microsoft para ver y crear pedidos.{' '}
          <button type="button" className="link-button" onClick={() => login()}>Iniciar sesión</button>
        </p>
      )}
      <div className="grid">
        <OrderForm onSubmit={handleCreate} disabled={!canUseApi} />
        <OrdersTable orders={orders} loading={loading} error={error} />
      </div>
    </>
  )
}
