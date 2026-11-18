import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useApi } from '../../api/ContextHooks'
import type { Checklist, Recipe } from '../../api/types'
import { State } from '../../components/State'
import { num, range } from '../../lib/format'

/** Итог подтверждённого листа: созданная партия, замеры и предупреждения. */
export function CompletedView({ checklist, recipe }: { checklist: Checklist; recipe: Recipe }) {
  const api = useApi()
  const batches = useQuery({ queryKey: ['batches', 'all', 0], queryFn: () => api.listBatches({}) })
  const batch = batches.data?.items.find((b) => b.id === checklist.batchId)
  const byId = new Map(recipe.items.map((i) => [i.id, i]))

  return (
    <div className="stack">
      <div className="note ok">
        Лист подтверждён.{' '}
        {batch ? (
          <>
            Создана партия <Link to={`/batches/${encodeURIComponent(batch.batchNo)}`}>{batch.batchNo}</Link>
            {batch.status === 'QUARANTINE' ? ' — она на карантине: замер готового продукта вне допуска.' : '.'}
          </>
        ) : (
          'Партия создана.'
        )}
      </div>
      {checklist.reviewSummary && <div className="note warn">Записанные отклонения: {checklist.reviewSummary}</div>}
      <div className="two-col">
        <div className="panel">
          <header><h2>Навески</h2></header>
          <div className="table-wrap">
            <table className="grid">
              <thead>
                <tr><th>Компонент</th><th className="n">План, кг</th><th className="n">Факт, кг</th><th>Состояние</th></tr>
              </thead>
              <tbody>
                {checklist.items.map((i) => {
                  const ri = i.recipeItemId ? byId.get(i.recipeItemId) : undefined
                  return (
                    <tr key={i.id}>
                      <td>{ri?.materialCode ?? i.materialNameRaw}</td>
                      <td className="n">{num(ri?.targetQty)}</td>
                      <td className="n">{num(i.weight)}</td>
                      <td>{i.withinTolerance === false ? <State tone="out">Вне допуска</State> : <State tone="ok">Норма</State>}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
        <div className="panel">
          <header><h2>Контрольные замеры</h2></header>
          <div className="table-wrap">
            <table className="grid">
              <thead><tr><th>Параметр</th><th className="n">Значение</th><th>Допуск</th><th>Состояние</th></tr></thead>
              <tbody>
                {checklist.controlRecords.map((c) => (
                  <tr key={c.id}>
                    <td>{c.parameterName}</td>
                    <td className="n">{num(c.measuredValue)}</td>
                    <td>{range(c.expectedMin, c.expectedMax)}</td>
                    <td>{c.withinRange ? <State tone="ok">В допуске</State> : <State tone="out">Вне допуска</State>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}
