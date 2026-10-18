import { useCallback, useEffect, useState } from 'react'
import Header from './components/Header.jsx'
import Footer from './components/Footer.jsx'
import HomePage from './pages/HomePage.jsx'
import OrdersPage from './pages/OrdersPage.jsx'
import { checkBackend } from './services/api.js'

function getRoute() {
  return window.location.hash === '#/pedidos' ? 'pedidos' : 'inicio'
}

export default function App() {
  const [route, setRoute] = useState(getRoute)
  const [backend, setBackend] = useState({ status: 'checking' })

  useEffect(() => {
    const onHashChange = () => setRoute(getRoute())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const refreshBackend = useCallback(async (signal) => {
    setBackend({ status: 'checking' })
    try {
      setBackend(await checkBackend(signal))
    } catch {
      // Petición cancelada: no se actualiza el estado.
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    refreshBackend(controller.signal)
    return () => controller.abort()
  }, [refreshBackend])

  return (
    <>
      <a className="skip-link" href="#contenido">Saltar al contenido</a>
      <Header route={route} backend={backend} />
      <main id="contenido" className="container">
        {route === 'pedidos' ? (
          <OrdersPage backend={backend} onRetry={() => refreshBackend()} />
        ) : (
          <HomePage backend={backend} onRetry={() => refreshBackend()} />
        )}
      </main>
      <Footer />
    </>
  )
}
