import { InteractionRequiredAuthError, PublicClientApplication } from '@azure/msal-browser'
import { authSettings, isAuthConfigured } from './authConfig.js'

// OAuth 2.0 Authorization Code Flow con PKCE (MSAL lo aplica por defecto en aplicaciones SPA).
let msal = null

export async function initAuth() {
  if (!isAuthConfigured()) return
  msal = new PublicClientApplication({
    auth: {
      clientId: authSettings.clientId,
      authority: `https://login.microsoftonline.com/${authSettings.tenantId}`,
      redirectUri: `${window.location.origin}/`,
      postLogoutRedirectUri: `${window.location.origin}/`,
    },
    cache: { cacheLocation: 'sessionStorage' },
  })
  await msal.initialize()
  const result = await msal.handleRedirectPromise()
  const account = result?.account ?? msal.getActiveAccount() ?? msal.getAllAccounts()[0] ?? null
  if (account) msal.setActiveAccount(account)
}

export function getAccount() {
  return msal?.getActiveAccount() ?? null
}

export function login() {
  return msal.loginRedirect({ scopes: authSettings.apiScopes })
}

export function logout() {
  return msal.logoutRedirect({ account: getAccount() })
}

// Devuelve un access token JWT para la API de CloudOrders; renueva en silencio o redirige al login si hace falta.
export async function getAccessToken() {
  const account = getAccount()
  if (!msal || !account) return null
  try {
    const result = await msal.acquireTokenSilent({ scopes: authSettings.apiScopes, account })
    return result.accessToken
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      await msal.acquireTokenRedirect({ scopes: authSettings.apiScopes, account })
      return null
    }
    throw error
  }
}
