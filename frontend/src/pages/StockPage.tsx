import { useMutation, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useApi } from '../api/ContextHooks'
import type { Lot } from '../api/types'
import { ErrorNote, QueryView } from '../components/Query'
import { State } from '../components/State'
import { date, num, parseNumber } from '../lib/format'

const DAY = 24 * 3600 * 1000

function expiryState(lot: Lot, today: Date) {
  if (!lot.expiry) return <span className="muted small">без срока</span>
  const days = Math.floor((Date.parse(lot.expiry) - today.getTime()) / DAY)
  if (days < 0) return <State tone="bad">просрочен</State>
  if (days <= 14) return <State tone="review">истекает через {days} дн.</State>
  return <State tone="ok">годен</State>
}

/** Сырьё на складе по сроку годности и расчёт выдачи по FEFO (первым уходит то, что раньше истекает). */
export function StockPage() {
  const api = useApi()
  const [filter, setFilter] = useState('')
  const lots = useQuery({ queryKey: ['lots', filter], queryFn: () => api.listLots({ materialCode: filter }) })
  const all = useQuery({ queryKey: ['lots', ''], queryFn: () => api.listLots({}) })
  const materials = useMemo(() => {
    const map = new Map<string, string>()
    all.data?.items.forEach((l) => map.set(l.materialId, l.materialCode ?? l.materialId))
    return [...map.entries()]
  }, [all.data])
  const [materialId, setMaterialId] = useState('')
  const [qty, setQty] = useState('')
  const allocate = useMutation({ mutationFn: () => api.allocate(materialId, parseNumber(qty) ?? 0) })
  const today = new Date()

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Сырьё</h1>
          <p>Лоты отсортированы по сроку годности. Просроченные лоты в выдачу не попадают.</p>
        </div>
      </div>
      <div className="stock-layout">
        <div className="stack">
          <label className="field" style={{ maxWidth: 320 }}>
            Материал
            <input className="input" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Например, Компонент А" />
          </label>
          <QueryView query={lots}>
            {(data) => (
              <div className="table-wrap panel">
                <table className="grid">
                  <thead><tr><th>Лот</th><th>Материал</th><th className="n">Остаток, кг</th><th>Годен до</th><th>Срок</th></tr></thead>
                  <tbody>
                    {data.items.map((l) => (
                      <tr key={l.id}>
                        <td className="nowrap">{l.lotNo}</td>
                        <td>{l.materialCode}<span className="sub small muted" style={{ display: 'block' }}>{l.supplier}</span></td>
                        <td className="n">{num(l.quantityAvailable)}</td>
                        <td>{date(l.expiry)}</td>
                        <td>{expiryState(l, today)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </QueryView>
        </div>
        <div className="panel">
          <header><h2>Расчёт выдачи</h2></header>
          <div className="body stack">
            <form className="inline-form" onSubmit={(e) => { e.preventDefault(); allocate.mutate() }}>
              <label className="field">
                Материал
                <select className="input" value={materialId} onChange={(e) => setMaterialId(e.target.value)}>
                  <option value="">— выберите —</option>
                  {materials.map(([id, code]) => <option key={id} value={id}>{code}</option>)}
                </select>
              </label>
              <label className="field">
                Нужно, кг
                <input className="input num" inputMode="decimal" value={qty} onChange={(e) => setQty(e.target.value)} />
              </label>
              <button className="btn primary" disabled={!materialId || !(parseNumber(qty) ?? 0)}>Рассчитать</button>
            </form>
            {allocate.isError && <ErrorNote error={allocate.error} />}
            {allocate.data && (
              <>
                {allocate.data.complete ? (
                  <State tone="ok">Хватает: выдать из {allocate.data.picks.length} лот(ов)</State>
                ) : (
                  <State tone="bad">Не хватает {num(allocate.data.shortage)} кг</State>
                )}
                <table className="grid">
                  <caption>Порядок выдачи</caption>
                  <thead><tr><th>Лот</th><th>Годен до</th><th className="n">Выдать, кг</th></tr></thead>
                  <tbody>
                    {allocate.data.picks.map((p) => (
                      <tr key={p.lotId}><td className="nowrap">{p.lotNo}</td><td>{date(p.expiry)}</td><td className="n">{num(p.quantity)}</td></tr>
                    ))}
                  </tbody>
                </table>
                <span className="small muted">Это расчёт: остатки спишутся при выдаче кладовщиком.</span>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
