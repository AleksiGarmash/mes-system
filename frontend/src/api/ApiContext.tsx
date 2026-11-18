import type { ReactNode } from 'react'
import type { MesApi } from './client'
import { ApiContext } from './ContextHooks'

export function ApiProvider({ api, children }: { api: MesApi; children: ReactNode }) {
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>
}

