import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import type { BatchStatus } from '../api/types'
import { Pager } from '../components/Pager'
import { QueryView } from '../components/Query'
import { StatusLabel } from '../components/State'
import { batchStatus } from '../components/labels'
import { dateTime, num } from '../lib/format'

const FILTERS: { label: string; status?: BatchStatus }[] = [
  { label: 'Все' },
  { label: 'Ждут контроля', status: 'PENDING_QC' },
  { label: 'Карантин', status: 'QUARANTINE' },
  { label: 'Забракованы', status: 'REJECTED' },
  { label: 'Выпущены', status: 'RELEASED' },
]

export function BatchesPage() {
  const api = useApi()
  const [filter, setFilter] = useState(0)
  const [page, setPage] = useState(0)
  const status = FILTERS[filter].status
  const query = useQuery({ queryKey: ['batches', status ?? 'all', page], queryFn: () => api.listBatches({ status, page }) })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Партии</h1>
          <p>Откройте партию, чтобы увидеть, из каких лотов сырья она сделана и что показал контроль качества.</p>
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
        {(data) => (
          <>
            <div className="table-wrap panel">
              <table className="grid">
                <thead>
                  <tr><th>Партия</th><th>Задание</th><th className="n">Масса, кг</th><th>Произведена</th><th>Оператор</th><th>Состояние</th></tr>
                </thead>
                <tbody>
                  {data.items.map((b) => (
                    <tr key={b.id}>
                      <td><Link to={`/batches/${encodeURIComponent(b.batchNo)}`}>{b.batchNo}</Link></td>
                      <td>№{b.orderNo}</td>
                      <td className="n">{num(b.qty)}</td>
                      <td>{dateTime(b.producedAt)}</td>
                      <td>{b.operator ?? '—'}</td>
                      <td><StatusLabel value={batchStatus[b.status]} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </QueryView>
    </>
  )
}
