import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import { ApiProvider } from './api/ApiContext'
import { App } from './App'
import { bootstrap } from './auth/auth'
import './styles/app.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

const root = createRoot(document.getElementById('root')!)

bootstrap()
  .then(({ api, logout }) =>
    root.render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <ApiProvider api={api}>
            {/* HashRouter: демо-сборка работает с любого статического хостинга без настройки маршрутов */}
            <HashRouter>
              <App onLogout={logout} />
            </HashRouter>
          </ApiProvider>
        </QueryClientProvider>
      </StrictMode>,
    ),
  )
  .catch((e: unknown) => {
    root.render(
      <div className="main">
        <div className="note bad" role="alert">
          Не удалось войти: {e instanceof Error ? e.message : String(e)}. Проверьте, что Keycloak доступен.
        </div>
      </div>,
    )
  })
