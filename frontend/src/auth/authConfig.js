// Configuración de Microsoft Entra ID (solo valores públicos: un SPA no puede guardar secretos).
//   VITE_ENTRA_CLIENT_ID   Application (client) ID del registro "CloudOrders SPA"
//   VITE_ENTRA_TENANT_ID   Directory (tenant) ID
//   VITE_ENTRA_API_SCOPES  scopes de la API separados por coma, p. ej.
//                          api://<api-client-id>/orders.read,api://<api-client-id>/orders.write
const env = import.meta.env

export const authSettings = {
  clientId: (env.VITE_ENTRA_CLIENT_ID || '').trim(),
  tenantId: (env.VITE_ENTRA_TENANT_ID || '').trim(),
  apiScopes: (env.VITE_ENTRA_API_SCOPES || '').split(/[\s,]+/).filter(Boolean),
}

export function isAuthConfigured() {
  return Boolean(authSettings.clientId && authSettings.tenantId && authSettings.apiScopes.length > 0)
}
