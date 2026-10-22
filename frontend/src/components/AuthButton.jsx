import { getAccount, login, logout } from '../auth/auth.js'
import { isAuthConfigured } from '../auth/authConfig.js'

export default function AuthButton() {
  if (!isAuthConfigured()) {
    return <span className="auth auth--off">Login no configurado</span>
  }
  const account = getAccount()
  if (!account) {
    return (
      <button type="button" className="btn btn--secondary" onClick={() => login()}>
        Iniciar sesión
      </button>
    )
  }
  return (
    <span className="auth">
      <span className="auth__user">{account.name || account.username}</span>
      <button type="button" className="btn btn--secondary" onClick={() => logout()}>
        Cerrar sesión
      </button>
    </span>
  )
}
