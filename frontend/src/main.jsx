import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import { initAuth } from './auth/auth.js'
import './styles/global.css'

// Procesa la respuesta del login (redirect de Entra ID) antes de mostrar la aplicación.
initAuth()
  .catch((error) => console.error('No se pudo inicializar el inicio de sesión:', error))
  .finally(() => {
    createRoot(document.getElementById('root')).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
