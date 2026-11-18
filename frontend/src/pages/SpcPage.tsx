import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useApi } from '../api/ContextHooks'
import type { SpcResult, SpcSource } from '../api/types'
import { ControlChart } from '../charts/ControlChart'
import { QueryView } from '../components/Query'
import { State } from '../components/State'
import { dateTime, num } from '../lib/format'

const PARAMETERS: { label: string; parameter: string; source: SpcSource }[] = [
  { label: 'pH (протокол качества)', parameter: 'Водородный показатель pH', source: 'QC' },
  { label: 'Плотность (протокол качества)', parameter: 'Плотность', source: 'QC' },
]

/** Оценка воспроизводимости: ориентир Cpk ≥ 1,33; ниже 1,0 процесс выпускает брак. */
function capabilityVerdict(cpk: number | null) {
  if (cpk === null) return <span className="muted small">нет границ допуска</span>
  if (cpk >= 1.33) return <State tone="ok">достаточно</State>
  if (cpk >= 1) return <State tone="review">на грани</State>
  return <State tone="bad">недостаточно</State>
}

export function SpcPage() {
  const api = useApi()
  const [recipeCode, setRecipeCode] = useState('TF-01')
  const [choice, setChoice] = useState(0)
  const [showTable, setShowTable] = useState(false)
  const p = PARAMETERS[choice]
  const query = useQuery({
    queryKey: ['spc', recipeCode, p.parameter, p.source],
    queryFn: () => api.spc({ recipeCode, parameter: p.parameter, source: p.source }),
  })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Стабильность процесса</h1>
          <p>
            Пунктир — границы, в которых процесс держится сам (±3σ). Сплошная линия — требование допуска.
            Точки с кольцом требуют разбора.
          </p>
        </div>
      </div>
      <div className="inline-form" style={{ marginBottom: 16 }}>
        <label className="field">
          Рецептура
          <input className="input" value={recipeCode} onChange={(e) => setRecipeCode(e.target.value.trim())} />
        </label>
        <label className="field">
          Параметр
          <select className="input" value={choice} onChange={(e) => setChoice(Number(e.target.value))}>
            {PARAMETERS.map((x, i) => <option key={x.label} value={i}>{x.label}</option>)}
          </select>
        </label>
      </div>
      <QueryView query={query}>
        {(data) => <SpcBody data={data} showTable={showTable} onToggleTable={() => setShowTable((v) => !v)} />}
      </QueryView>
    </>
  )
}

function SpcBody({ data, showTable, onToggleTable }: { data: SpcResult; showTable: boolean; onToggleTable: () => void }) {
  const r = data.report
  const outOfSpec = data.points.filter((pt) => pt.outOfSpec).length
  const uniqueSignals = [...new Map(r.signals.map((s) => [s.rule, s])).values()]

  return (
    <div className="spc-layout">
      <div className="panel">
        <header>
          <h2>{data.parameter}</h2>
          <button className="btn small" onClick={onToggleTable} aria-pressed={showTable}>
            {showTable ? 'Показать график' : 'Показать таблицей'}
          </button>
        </header>
        <div className="body">
          {!r.sufficientData && (
            <div className="note warn" style={{ marginBottom: 12 }}>
              Измерений {r.n}: для надёжной оценки нужно хотя бы 20. Выводы предварительные.
            </div>
          )}
          {showTable ? (
            <div className="table-wrap">
              <table className="grid">
                <thead><tr><th>Время</th><th>Партия</th><th className="n">Значение</th><th>Оценка</th></tr></thead>
                <tbody>
                  {data.points.map((pt, i) => (
                    <tr key={i}>
                      <td>{dateTime(pt.at)}</td>
                      <td>{pt.label}</td>
                      <td className="n">{num(pt.value, 3)}</td>
                      <td>
                        {pt.outOfSpec ? <State tone="bad">вне допуска</State>
                          : r.signals.some((s) => s.index === i) ? <State tone="review">сигнал</State>
                          : <State tone="ok">норма</State>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <ControlChart data={data} />
          )}
        </div>
      </div>

      <aside className="stack">
        <div className="panel">
          <header><h2>Воспроизводимость</h2></header>
          <div className="body kpis">
            <div className="kpi"><span>Cpk</span><span><b>{num(r.cpk, 2)}</b> {capabilityVerdict(r.cpk)}</span></div>
            <div className="kpi"><span>Cp</span><b>{num(r.cp, 2)}</b></div>
            <div className="kpi"><span>Ppk</span><b>{num(r.ppk, 2)}</b></div>
            <div className="kpi"><span>Вне допуска</span><b>{outOfSpec} из {r.n}</b></div>
            <p className="small muted" style={{ margin: '10px 0 0' }}>
              Cp сравнивает ширину допуска с разбросом, Cpk ещё учитывает смещение среднего. Ориентир — 1,33 и выше.
            </p>
          </div>
        </div>
        <div className="panel">
          <header><h2>Сигналы</h2></header>
          <div className="body">
            {uniqueSignals.length === 0 ? (
              <State tone="ok">Процесс стабилен</State>
            ) : (
              <ul className="signals">
                {uniqueSignals.map((s) => (
                  <li key={s.rule}>
                    <State tone="review">{s.description}</State>
                    <span className="small muted" style={{ display: 'block' }}>
                      впервые на точке {data.points[r.signals.find((x) => x.rule === s.rule)!.index]?.label}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </aside>
    </div>
  )
}
