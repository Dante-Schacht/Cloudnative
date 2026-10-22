import BackendStatus from './BackendStatus.jsx'
import AuthButton from './AuthButton.jsx'

const links = [
  { route: 'inicio', href: '#/', label: 'Inicio' },
  { route: 'pedidos', href: '#/pedidos', label: 'Pedidos' },
]

export default function Header({ route, backend }) {
  return (
    <header className="header">
      <div className="container header__inner">
        <a className="brand" href="#/">
          <span className="brand__logo" aria-hidden="true">☁</span>
          CloudOrders
        </a>
        <nav aria-label="Navegación principal">
          <ul className="nav">
            {links.map((link) => (
              <li key={link.route}>
                <a
                  href={link.href}
                  className={route === link.route ? 'nav__link nav__link--active' : 'nav__link'}
                  aria-current={route === link.route ? 'page' : undefined}
                >
                  {link.label}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <BackendStatus status={backend.status} compact />
        <AuthButton />
      </div>
    </header>
  )
}
