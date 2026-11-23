import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useApi } from '../../api/ContextHooks'
import type { QcChecklist, QualitySpec } from '../../api/types'
import { State } from '../../components/State'
import { dateTime, num } from '../../lib/format'
import { buildQcRows } from '../../lib/qcReview'
import { Norm } from './Norm'

/** Итог подтверждённого протокола: что стало с партией и с какими результатами. */
export function QcCompletedView({ protocol, spec }: { protocol: QcChecklist; spec: QualitySpec | undefined }) {
  const api = useApi()
  const genealogy = useQuery({ queryKey: ['genealogy', protocol.batchNo], queryFn: () => api.genealogy(protocol.batchNo) })
  const rows = buildQcRows(protocol.items, spec, {})
  const failed = rows.filter((r) => r.item.withinRange !== true)
  const batchLink = <Link to={`/batches/${encodeURIComponent(protocol.batchNo)}`}>{protocol.batchNo}</Link>
  const status = genealogy.data?.status

  return (
    <div className="stack">
      {failed.length > 0 ? (
        <div className="note bad">
          Протокол подтверждён. Партия {batchLink} {status === 'REJECTED' ? 'забракована' : 'не прошла контроль'}: вне нормы{' '}
          {failed.map((r) => r.spec?.parameterName ?? r.item.parameterNameRaw).join(', ')}.
        </div>
      ) : (
        <div className="note ok">
          Протокол подтверждён. Партия {batchLink} {status === 'RELEASED' ? 'выпущена' : 'прошла контроль'}.
        </div>
      )}
      <div className="two-col qc-done">
        <div className="panel">
          <header><h2>Результаты</h2></header>
          <div className="table-wrap">
            <table className="grid">
              <thead>
                <tr><th>Параметр</th><th>Норма</th><th className="n">Значение</th><th>Результат</th></tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.item.id}>
                    <td>
                      {r.spec?.parameterName ?? r.item.parameterNameRaw}
                      {r.item.correctedByOperator && <span className="small muted block">проверено вручную</span>}
                    </td>
                    <td className="norm"><Norm spec={r.spec} /></td>
                    <td className="n">{r.qualitative ? r.text : `${num(r.value)}${r.spec?.unit ? ` ${r.spec.unit}` : ''}`}</td>
                    <td>
                      {r.item.withinRange === true ? <State tone="ok">Соответствует</State> : <State tone="out">Не соответствует</State>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div className="panel">
          <header><h2>Подписи и заключение</h2></header>
          <dl className="body facts">
            <dt>Подтвердил</dt>
            <dd>{protocol.confirmedBy ?? '—'}, {dateTime(protocol.confirmedAt)}</dd>
            <dt>Вторая подпись</dt>
            <dd>{protocol.secondSignatory ?? '—'}</dd>
            <dt>Решение по партии</dt>
            <dd>{protocol.decisionNote ?? '—'}</dd>
            {(protocol.nonconformityActions || failed.length > 0) && (
              <>
                <dt>Мероприятия по несоответствиям</dt>
                <dd>{protocol.nonconformityActions ?? '—'}</dd>
              </>
            )}
          </dl>
        </div>
      </div>
    </div>
  )
}
