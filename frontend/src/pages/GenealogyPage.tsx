import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import { QueryView } from '../components/Query'
import { State, StatusLabel } from '../components/State'
import { batchStatus, qcResult } from '../components/labels'
import { date, dateTime, num, range } from '../lib/format'

/** Родословная партии: прослеживаемость «назад» — сырьё, навески, контроль качества. */
export function GenealogyPage() {
  const { batchNo = '' } = useParams()
  const api = useApi()
  const query = useQuery({ queryKey: ['genealogy', batchNo], queryFn: () => api.genealogy(batchNo) })

  return (
    <QueryView query={query}>
      {(g) => (
        <>
          <div className="page-head">
            <div>
              <p className="small" style={{ margin: 0 }}><Link to="/batches">Партии</Link></p>
              <h1>Партия {g.batchNo}</h1>
              <p>Задание №{g.orderNo}, рецептура {g.recipeCode} версия {g.recipeVersion}, произведена {dateTime(g.producedAt)}</p>
            </div>
            <StatusLabel value={batchStatus[g.status]} />
          </div>
          <div className="stack">
            <div className="panel">
              <header><h2>Из чего сделана</h2></header>
              <div className="table-wrap">
                <table className="grid">
                  <thead>
                    <tr><th>Стадия</th><th>Компонент</th><th>Лот сырья</th><th>Поставщик</th><th>Годен до</th><th className="n">План, кг</th><th className="n">Факт, кг</th><th>Дозировка</th></tr>
                  </thead>
                  <tbody>
                    {g.components.map((c, i) => (
                      <tr key={i}>
                        <td>{c.stageNo ?? '—'}</td>
                        <td>{c.materialCode}</td>
                        <td className="nowrap">{c.lotNo ? <Link to={`/recall?lot=${encodeURIComponent(c.lotNo)}`}>{c.lotNo}</Link> : <span className="muted">—</span>}</td>
                        <td>{c.supplier ?? '—'}</td>
                        <td>{date(c.expiry)}</td>
                        <td className="n">{num(c.targetQty)}</td>
                        <td className="n">{num(c.actualQty)}</td>
                        <td>{c.withinTolerance ? <State tone="ok">В допуске</State> : <State tone="out">Отклонение</State>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <div className="panel">
              <header><h2>Контроль качества</h2></header>
              {g.qualityChecks.length === 0 ? (
                <div className="empty">
                  Протокол качества ещё не подтверждён.
                  {g.status === 'PENDING_QC' && (
                    <>
                      {' '}
                      <Link to="/qc">Перейти к протоколам качества</Link>
                    </>
                  )}
                </div>
              ) : (
                <div className="table-wrap">
                  <table className="grid">
                    <thead><tr><th>Параметр</th><th className="n">Результат</th><th>Требование</th><th>Вывод</th></tr></thead>
                    <tbody>
                      {g.qualityChecks.map((q) => (
                        <tr key={q.parameter}>
                          <td>{q.parameter}</td>
                          <td className="n">{q.textValue ?? num(q.measuredValue)}</td>
                          <td>{q.expectedText ? q.expectedText.split(';').join(' или ') : range(q.expectedMin, q.expectedMax)}</td>
                          <td><StatusLabel value={qcResult[q.result]} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </QueryView>
  )
}
