import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import type { ChecklistStatus } from '../api/types'
import { Pager } from '../components/Pager'
import { Empty, QueryView } from '../components/Query'
import { State, StatusLabel } from '../components/State'
import { checklistStatus } from '../components/labels'
import { dateTime } from '../lib/format'

const FILTERS: { label: string; status?: ChecklistStatus }[] = [
  { label: 'Ждут проверки', status: 'WAITING_CONFIRMATION' },
  { label: 'Ждут фото', status: 'NEW' },
  { label: 'Партия создана', status: 'PRODUCTION_COMPLETED' },
  { label: 'Все' },
]

export function ChecklistsPage() {
  const api = useApi()
  const [filter, setFilter] = useState(0)
  const [page, setPage] = useState(0)
  const status = FILTERS[filter].status
  const query = useQuery({ queryKey: ['checklists', status ?? 'all', page], queryFn: () => api.listChecklists({ status, page }) })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Чек-листы</h1>
          <p>Бумажный бланк остаётся официальным документом. Здесь его фото превращается в проверенную запись о партии.</p>
        </div>
      </div>
      <div className="tabs" role="group" aria-label="Фильтр по состоянию">
        {FILTERS.map((f, i) => (
          <button key={f.label} className="tab" aria-pressed={i === filter} onClick={() => { setFilter(i); setPage(0) }}>
            {f.label}
          </button>
        ))}
      </div>
      <QueryView query={query}>
        {(data) =>
          data.items.length === 0 ? (
            <div className="panel">
              <Empty title="Здесь пусто">
                {status === 'WAITING_CONFIRMATION' ? 'Все распознанные листы проверены.' : 'Создайте чек-лист из раздела «Задания».'}
              </Empty>
            </div>
          ) : (
            <>
              <div className="table-wrap panel">
                <table className="grid">
                  <thead>
                    <tr>
                      <th>Задание</th>
                      <th>Рецептура</th>
                      <th>Состояние</th>
                      <th className="n">Строк проверить</th>
                      <th>Фото загружено</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((c) => (
                      <tr key={c.id}>
                        <td><Link to={`/checklists/${c.id}`}>№{c.orderNo}</Link></td>
                        <td>{c.recipeCode}</td>
                        <td><StatusLabel value={checklistStatus[c.status]} /></td>
                        <td className="n">{c.needsReviewCount > 0 ? <State tone="review">{c.needsReviewCount}</State> : '—'}</td>
                        <td>{dateTime(c.uploadedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} />
            </>
          )
        }
      </QueryView>
    </>
  )
}
