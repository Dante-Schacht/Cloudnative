// Contrato HTTP con el backend Spring Boot (order-service):
//   GET  /api/health  -> 200 si el backend está operativo (público)
//   GET  /api/orders  -> lista de pedidos [{ id, customerName, product, quantity, notes, status, createdAt }]
//   POST /api/orders  -> crea un pedido { customerName, product, quantity, notes } y devuelve el pedido creado
// /api/orders exige un access token JWT de Microsoft Entra ID (cabecera Authorization: Bearer).
// Estados válidos: PENDING, PROCESSING, COMPLETED, FAILED.
// VITE_API_BASE_URL apunta al backend en local y a la URL de API Gateway en el despliegue cloud.
import { getAccessToken, getAccount } from '../auth/auth.js'
import { isAuthConfigured } from '../auth/authConfig.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/$/, '')

export const ENDPOINTS = {
  health: '/api/health',
  orders: '/api/orders',
}

export const ORDER_STATUSES = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']

const REQUEST_TIMEOUT_MS = 8000

export class ApiError extends Error {
  constructor(message, { status } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function isApiConfigured() {
  return API_BASE_URL !== ''
}

async function bearerHeaders(authenticated) {
  if (!authenticated) return {}
  if (!isAuthConfigured()) {
    throw new ApiError('El inicio de sesión no está configurado (VITE_ENTRA_*).', { status: 401 })
  }
  if (!getAccount()) {
    throw new ApiError('Debes iniciar sesión para usar esta función.', { status: 401 })
  }
  const token = await getAccessToken()
  if (!token) throw new ApiError('Redirigiendo al inicio de sesión…', { status: 401 })
  return { Authorization: `Bearer ${token}` }
}

async function request(path, { signal, parse = true, authenticated = true, ...options } = {}) {
  if (!isApiConfigured()) {
    throw new ApiError('La URL del backend no está configurada (VITE_API_BASE_URL).')
  }

  const authHeaders = await bearerHeaders(authenticated)
  const timeout = AbortSignal.timeout(REQUEST_TIMEOUT_MS)
  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: { Accept: 'application/json', ...authHeaders, ...options.headers },
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError('No se pudo conectar con el backend. Verifica que esté en ejecución.')
  }

  if (response.status === 401 || response.status === 403) {
    throw new ApiError(
      response.status === 401
        ? 'Tu sesión no es válida o expiró. Vuelve a iniciar sesión.'
        : 'Tu usuario no tiene permisos para esta operación.',
      { status: response.status },
    )
  }
  if (!response.ok) {
    throw new ApiError(`El backend respondió con un error (código ${response.status}).`, {
      status: response.status,
    })
  }

  if (!parse || response.status === 204) return null
  try {
    return await response.json()
  } catch {
    throw new ApiError('La respuesta del backend no tiene un formato válido.')
  }
}

export async function listOrders(signal) {
  const data = await request(ENDPOINTS.orders, { signal })
  if (!Array.isArray(data)) {
    throw new ApiError('El backend devolvió un listado de pedidos inválido.')
  }
  return data
}

export function createOrder(order) {
  return request(ENDPOINTS.orders, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(order),
  })
}

// Devuelve { status: 'connected' | 'disconnected' | 'unconfigured' | 'error' }. Solo lanza si se cancela.
export async function checkBackend(signal) {
  if (!isApiConfigured()) return { status: 'unconfigured' }
  try {
    await request(ENDPOINTS.health, { signal, parse: false, authenticated: false })
    return { status: 'connected' }
  } catch (error) {
    if (signal?.aborted) throw error
    return { status: error.status ? 'error' : 'disconnected' }
  }
}
