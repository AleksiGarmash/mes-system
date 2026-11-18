import { createContext, useContext } from 'react'
import type { MesApi } from './client'

export const ApiContext = createContext<MesApi | null>(null)

/** Текущая реализация API (настоящий бэкенд или демо). Подменяется в тестах. */
export function useApi(): MesApi {
  const api = useContext(ApiContext)
  if (!api) throw new Error('useApi вызван вне ApiProvider')
  return api
}
