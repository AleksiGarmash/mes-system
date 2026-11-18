import Keycloak from 'keycloak-js'
import type { MesApi } from '../api/client'
import { HttpApi } from '../api/http'
import { DemoApi } from '../api/demo/demoApi'

export const isDemo = import.meta.env.VITE_API_MODE === 'demo'

/**
 * Запуск приложения: в демо-режиме — API в памяти без входа; в обычном — вход через Keycloak
 * (Authorization Code + PKCE) и HTTP-клиент, который перед каждым запросом обновляет токен.
 */
export async function bootstrap(): Promise<{ api: MesApi; logout: () => void }> {
  if (isDemo) {
    return { api: new DemoApi(), logout: () => window.location.reload() }
  }

  const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'mes',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT ?? 'mes-frontend',
  })
  await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })

  const token = async () => {
    // обновить токен, если он истекает в ближайшие 30 секунд
    await keycloak.updateToken(30).catch(() => keycloak.login())
    return keycloak.token
  }
  return {
    api: new HttpApi(import.meta.env.VITE_API_BASE ?? '', token),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  }
}
