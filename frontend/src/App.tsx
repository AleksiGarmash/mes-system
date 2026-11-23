import { useQuery } from '@tanstack/react-query'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useApi } from './api/ContextHooks'
import type { Role } from './api/types'
import { Layout } from './components/Layout'
import { QueryView } from './components/Query'
import { BatchesPage } from './pages/BatchesPage'
import { ChecklistPage } from './pages/ChecklistPage'
import { ChecklistsPage } from './pages/ChecklistsPage'
import { GenealogyPage } from './pages/GenealogyPage'
import { OrdersPage } from './pages/OrdersPage'
import { QcPage } from './pages/qc/QcPage'
import { QcProtocolPage } from './pages/qc/QcProtocolPage'
import { RecallPage } from './pages/RecallPage'
import { SpcPage } from './pages/SpcPage'
import { StockPage } from './pages/StockPage'

export function App({ onLogout }: { onLogout: () => void }) {
  const api = useApi()
  const me = useQuery({ queryKey: ['me'], queryFn: () => api.me(), staleTime: Infinity })

  return (
    <QueryView query={me}>
      {(user) => (
        <Routes>
          <Route element={<Layout roles={user.roles} username={user.username} onLogout={onLogout} />}>
            <Route index element={<Navigate to={startPage(user.roles)} replace />} />
            <Route path="checklists" element={<ChecklistsPage />} />
            <Route path="checklists/:id" element={<ChecklistPage operator={user.username} />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="qc" element={<QcPage />} />
            <Route path="qc/:id" element={<QcProtocolPage labTechnician={user.username} />} />
            <Route path="batches" element={<BatchesPage />} />
            <Route path="batches/:batchNo" element={<GenealogyPage />} />
            <Route path="recall" element={<RecallPage />} />
            <Route path="stock" element={<StockPage />} />
            <Route path="spc" element={<SpcPage />} />
            <Route path="*" element={<div className="empty"><strong>Страница не найдена</strong>Проверьте адрес или выберите раздел слева.</div>} />
          </Route>
        </Routes>
      )}
    </QueryView>
  )
}

/** Первый экран — рабочее место роли: кладовщику склад, лаборанту протоколы качества. */
function startPage(roles: Role[]): string {
  const only = (role: Role) => roles.includes(role) && !roles.some((r) => r === 'ADMIN' || r === 'OPERATOR' || r === 'TECHNOLOGIST')
  if (only('LAB_TECHNICIAN')) return '/qc'
  if (only('STOREKEEPER')) return '/stock'
  return '/checklists'
}
