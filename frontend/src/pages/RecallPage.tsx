import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import { ErrorNote, Empty, QueryView } from '../components/Query'
import { StatusLabel } from '../components/State'
import { batchStatus } from '../components/labels'
import { date, dateTime, num } from '../lib/format'

/** Отзыв лота сырья: прослеживаемость «вперёд» и перевод затронутых партий на карантин. */
export function RecallPage() {
  const api = useApi()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const lot = params.get('lot') ?? ''
  const [input, setInput] = useState(lot)
  const [reason, setReason] = useState('')
  const [confirming, setConfirming] = useState(false)

  const report = useQuery({ queryKey: ['recall', lot], queryFn: () => api.recallReport(lot), enabled: lot !== '' })
  const quarantine = useMutation({
    mutationFn: () => api.quarantineLot(lot, reason.trim()),
    onSuccess: () => {
      setConfirming(false)
      queryClient.invalidateQueries({ queryKey: ['recall', lot] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
  })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Отзыв сырья</h1>
          <p>Введите номер лота от поставщика: система покажет все партии, в которые он попал, и переведёт их на карантин.</p>
        </div>
      </div>
      <form
        className="inline-form"
        style={{ marginBottom: 18 }}
        onSubmit={(e) => {
          e.preventDefault()
          quarantine.reset()
          setParams(input.trim() ? { lot: input.trim() } : {})
        }}
      >
        <label className="field">
          Номер лота
          <input className="input" value={input} onChange={(e) => setInput(e.target.value)} placeholder="RM-2026-0390" />
        </label>
        <button className="btn primary" type="submit">Найти партии</button>
      </form>

      {lot === '' ? (
        <div className="panel"><Empty title="Лот не выбран">В демо можно проверить лот RM-2026-0390: он попал в шесть последних партий.</Empty></div>
      ) : (
        <QueryView query={report}>
          {(r) => {
            const eligible = r.affectedBatches.filter((b) => b.status !== 'REJECTED' && b.status !== 'QUARANTINE').length
            return (
              <div className="stack">
                <ul className="meta-list">
                  <li>Материал: <b>{r.materialCode ?? '—'}</b></li>
                  <li>Поставщик: <b>{r.supplier ?? '—'}</b></li>
                  <li>Годен до: <b>{date(r.expiry)}</b></li>
                  <li>Затронуто партий: <b>{r.affectedBatches.length}</b></li>
                </ul>
                {quarantine.data && (
                  <div className="note ok" role="status">
                    На карантин переведено партий: {quarantine.data.quarantinedBatchNos.length}.
                    {quarantine.data.skippedBatchNos.length > 0 && ` Пропущены (уже на карантине или забракованы): ${quarantine.data.skippedBatchNos.join(', ')}.`}
                  </div>
                )}
                {quarantine.isError && <ErrorNote error={quarantine.error} />}
                {r.affectedBatches.length === 0 ? (
                  <div className="panel"><Empty title="Лот не использовался">Ни одна партия не содержит этот лот.</Empty></div>
                ) : (
                  <div className="table-wrap panel">
                    <table className="grid">
                      <thead><tr><th>Партия</th><th>Задание</th><th>Рецептура</th><th>Произведена</th><th className="n">Использовано, кг</th><th>Состояние</th></tr></thead>
                      <tbody>
                        {r.affectedBatches.map((b) => (
                          <tr key={b.batchNo}>
                            <td><Link to={`/batches/${encodeURIComponent(b.batchNo)}`}>{b.batchNo}</Link></td>
                            <td>№{b.orderNo}</td>
                            <td>{b.recipeCode}</td>
                            <td>{dateTime(b.producedAt)}</td>
                            <td className="n">{num(b.usedQty)}</td>
                            <td><StatusLabel value={batchStatus[b.status]} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {eligible > 0 && (
                  <div className="panel">
                    <div className="body stack">
                      {!confirming ? (
                        <div className="actions">
                          <button className="btn danger" onClick={() => setConfirming(true)}>
                            Перевести на карантин: {eligible}
                          </button>
                          <span className="small muted">Партии в работе, ждущие контроля и уже выпущенные. Изменение записывается в журнал.</span>
                        </div>
                      ) : (
                        <>
                          <label className="field">
                            Причина отзыва
                            <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Например: претензия поставщика по составу" />
                          </label>
                          <div className="actions">
                            <button className="btn danger" disabled={quarantine.isPending} onClick={() => quarantine.mutate()}>
                              Подтвердить карантин {eligible} партий
                            </button>
                            <button className="btn quiet" onClick={() => setConfirming(false)}>Отмена</button>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )
          }}
        </QueryView>
      )}
    </>
  )
}
