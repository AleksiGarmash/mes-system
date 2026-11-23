import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useApi } from '../../api/ContextHooks'
import type { QcChecklistStatus } from '../../api/types'
import { Pager } from '../../components/Pager'
import { Empty, ErrorNote, QueryView } from '../../components/Query'
import { State, StatusLabel } from '../../components/State'
import { qcChecklistStatus } from '../../components/labels'
import { dateTime } from '../../lib/format'

const FILTERS: { label: string; status?: QcChecklistStatus }[] = [
  { label: 'Все' },
  { label: 'Ждут проверки', status: 'WAITING_CONFIRMATION' },
  { label: 'Ждут фото', status: 'NEW' },
  { label: 'Подтверждены', status: 'CONFIRMED' },
  { label: 'Отклонены', status: 'REJECTED' },
]

/**
 * Рабочее место лаборанта: партии, которым ещё не заведён протокол, и протоколы в работе.
 * Одна партия — один протокол, поэтому «ждут протокола» = партии на контроле без протокола.
 */
export function QcPage() {
  const api = useApi()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState(0)
  const [page, setPage] = useState(0)
  const status = FILTERS[filter].status

  const pending = useQuery({ queryKey: ['batches', 'PENDING_QC', 0], queryFn: () => api.listBatches({ status: 'PENDING_QC' }) })
  // Все протоколы одним запросом (до 100), чтобы понять, у каких партий протокол уже есть.
  const allProtocols = useQuery({ queryKey: ['qc', 'all-for-batches'], queryFn: () => api.listQcChecklists({ size: 100 }) })
  const protocols = useQuery({ queryKey: ['qc', status ?? 'all', page], queryFn: () => api.listQcChecklists({ status, page }) })

  const create = useMutation({
    mutationFn: (batchId: string) => api.createQcChecklist(batchId),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['qc'] })
      navigate(`/qc/${created.id}`)
    },
  })

  const withProtocol = new Set(allProtocols.data?.items.map((p) => p.batchId))
  const waitingForProtocol = pending.data?.items.filter((b) => !withProtocol.has(b.id)) ?? []

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Контроль качества</h1>
          <p>
            Результаты лаборатории вносятся с бумажного протокола: распечатайте бланк с QR-кодом партии, заполните его
            и загрузите фото. После подтверждения партия выпускается или бракуется по результатам.
          </p>
        </div>
      </div>

      <section className="panel" aria-labelledby="qc-waiting" style={{ marginBottom: 24 }}>
        <header>
          <h2 id="qc-waiting">Ждут протокола</h2>
          <span className="small muted">Партии на контроле, для которых протокол ещё не заведён</span>
        </header>
        {pending.isPending || allProtocols.isPending ? (
          <div className="loading" role="status">Загрузка…</div>
        ) : pending.isError ? (
          <ErrorNote error={pending.error} onRetry={() => pending.refetch()} />
        ) : waitingForProtocol.length === 0 ? (
          <Empty title="Новых партий на контроль нет">Протоколы для всех партий на контроле уже заведены.</Empty>
        ) : (
          <div className="table-wrap">
            <table className="grid">
              <thead>
                <tr><th>Партия</th><th>Задание</th><th>Произведена</th><th>Оператор</th><th aria-label="Действие" /></tr>
              </thead>
              <tbody>
                {waitingForProtocol.map((b) => (
                  <tr key={b.id}>
                    <td className="nowrap"><Link to={`/batches/${encodeURIComponent(b.batchNo)}`}>{b.batchNo}</Link></td>
                    <td>№{b.orderNo}</td>
                    <td>{dateTime(b.producedAt)}</td>
                    <td>{b.operator ?? '—'}</td>
                    <td className="n">
                      <button className="btn small primary" disabled={create.isPending} onClick={() => create.mutate(b.id)}>
                        Завести протокол
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {create.isError && <div className="body"><ErrorNote error={create.error} /></div>}
      </section>

      <h2 style={{ margin: '0 0 10px' }}>Протоколы</h2>
      <div className="tabs" role="group" aria-label="Фильтр по состоянию протокола">
        {FILTERS.map((f, i) => (
          <button key={f.label} className="tab" aria-pressed={i === filter} onClick={() => { setFilter(i); setPage(0) }}>
            {f.label}
          </button>
        ))}
      </div>
      <QueryView query={protocols}>
        {(data) =>
          data.items.length === 0 ? (
            <div className="panel"><Empty title="Протоколов нет">В этом состоянии сейчас нет ни одного протокола.</Empty></div>
          ) : (
            <>
              <div className="table-wrap panel">
                <table className="grid">
                  <thead>
                    <tr><th>Партия</th><th>Состояние</th><th>Проверить</th><th>Фото загружено</th><th>Заведён</th></tr>
                  </thead>
                  <tbody>
                    {data.items.map((p) => (
                      <tr key={p.id}>
                        <td className="nowrap"><Link to={`/qc/${p.id}`}>Протокол {p.batchNo}</Link></td>
                        <td><StatusLabel value={qcChecklistStatus[p.status]} /></td>
                        <td>
                          {p.status === 'WAITING_CONFIRMATION' && p.needsReviewCount > 0 ? (
                            <State tone="review">{p.needsReviewCount} из {p.itemCount}</State>
                          ) : (
                            <span className="muted">—</span>
                          )}
                        </td>
                        <td>{dateTime(p.uploadedAt)}</td>
                        <td>{dateTime(p.createdAt)}</td>
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
