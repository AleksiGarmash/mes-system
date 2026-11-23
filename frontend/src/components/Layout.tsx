import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import type { Role } from '../api/types'
import { isDemo } from '../auth/auth'

interface NavItem {
  to: string
  label: string
  roles: Role[]
  count?: number
}

/** Разделы показываются по ролям из JWT; сервер всё равно проверяет права сам. */
export function Layout({ roles, username, onLogout }: { roles: Role[]; username: string; onLogout: () => void }) {
  const api = useApi()
  const waiting = useQuery({
    queryKey: ['checklists', 'WAITING_CONFIRMATION', 0],
    queryFn: () => api.listChecklists({ status: 'WAITING_CONFIRMATION' }),
  })
  const labRoles: Role[] = ['LAB_TECHNICIAN', 'ADMIN']
  const qcWaiting = useQuery({
    queryKey: ['qc', 'WAITING_CONFIRMATION', 0],
    queryFn: () => api.listQcChecklists({ status: 'WAITING_CONFIRMATION' }),
    enabled: labRoles.some((r) => roles.includes(r)),
  })

  const groups: { title: string; items: NavItem[] }[] = [
    {
      title: 'Производство',
      items: [
        { to: '/checklists', label: 'Чек-листы', roles: ['OPERATOR', 'TECHNOLOGIST', 'ADMIN'], count: waiting.data?.totalElements },
        { to: '/orders', label: 'Задания', roles: ['PLANNER', 'OPERATOR', 'ADMIN'] },
      ],
    },
    {
      title: 'Качество',
      items: [
        { to: '/qc', label: 'Протоколы качества', roles: labRoles, count: qcWaiting.data?.totalElements },
        { to: '/batches', label: 'Партии', roles: ['TECHNOLOGIST', 'LAB_TECHNICIAN', 'PLANNER', 'ADMIN'] },
        { to: '/spc', label: 'Стабильность процесса', roles: ['TECHNOLOGIST', 'LAB_TECHNICIAN', 'ADMIN'] },
        { to: '/recall', label: 'Отзыв сырья', roles: ['TECHNOLOGIST', 'ADMIN'] },
      ],
    },
    { title: 'Склад', items: [{ to: '/stock', label: 'Сырьё', roles: ['STOREKEEPER', 'PLANNER', 'TECHNOLOGIST', 'ADMIN'] }] },
  ]

  const allowed = (item: NavItem) => item.roles.some((r) => roles.includes(r))

  return (
    <div className="shell">
      <aside className="rail">
        <NavLink to="/" className="brand">
          <b>Mixing MES</b>
          <span>смесевой участок</span>
        </NavLink>
        {groups.map((g) => {
          const items = g.items.filter(allowed)
          if (items.length === 0) return null
          return (
            <nav className="nav-group" key={g.title} aria-label={g.title}>
              <span>{g.title}</span>
              {items.map((item) => (
                <NavLink key={item.to} to={item.to} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
                  {item.label}
                  {item.count ? (
                    <span className="nav-count" aria-label={`ждут проверки: ${item.count}`}>
                      {item.count}
                    </span>
                  ) : null}
                </NavLink>
              ))}
            </nav>
          )
        })}
        <div className="rail-foot">
          {isDemo && <span className="demo-flag">Демо-режим: данные в браузере, сбрасываются при перезагрузке</span>}
          <span>{username}</span>
          <button className="btn small" onClick={onLogout}>
            {isDemo ? 'Сбросить демо' : 'Выйти'}
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
