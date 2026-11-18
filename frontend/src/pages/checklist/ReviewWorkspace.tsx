import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useState } from 'react'
import { useApi } from '../../api/ContextHooks'
import type { Checklist, Order, Recipe, RecipeStage } from '../../api/types'
import { DocumentViewer } from '../../components/DocumentViewer'
import { ErrorNote } from '../../components/Query'
import { State } from '../../components/State'
import { num, parseNumber, range } from '../../lib/format'
import {
  buildControls,
  buildCorrections,
  buildRows,
  readiness,
  stageTotal,
  type ReviewRow,
  type RowEdit,
} from '../../lib/review'
import { dosageBounds } from '../../lib/tolerance'

const STATE_LABEL: Record<ReviewRow['state'], string> = {
  ok: 'Норма',
  review: 'Проверить',
  out: 'Вне допуска',
  missing: 'Нет значения',
}

/**
 * Проверка распознанного листа: слева оригинал, справа «ведомость» с теми же строками, что на бумаге.
 * Оператор трогает только строки с меткой «Проверить», «Вне допуска» или «Нет значения».
 */
export function ReviewWorkspace({ checklist, order, recipe, operator }: {
  checklist: Checklist
  order: Order
  recipe: Recipe
  operator: string
}) {
  const api = useApi()
  const queryClient = useQueryClient()
  const [edits, setEdits] = useState<Record<string, RowEdit>>({})
  const [controls, setControls] = useState<Record<string, number | null>>({})
  const [editing, setEditing] = useState<string | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const rows = useMemo(() => buildRows(checklist.items, recipe, edits), [checklist.items, recipe, edits])
  const controlInputs = useMemo(() => buildControls(recipe, controls), [recipe, controls])
  const ready = readiness(rows, controlInputs)
  const loadFile = useCallback(() => api.checklistFile(checklist.id), [api, checklist.id])

  const refresh = (updated: Checklist) => {
    queryClient.setQueryData(['checklist', checklist.id], updated)
    queryClient.invalidateQueries({ queryKey: ['checklists'] })
    queryClient.invalidateQueries({ queryKey: ['batches'] })
  }

  const confirm = useMutation({
    mutationFn: () =>
      api.confirmChecklist(checklist.id, {
        operator,
        corrections: buildCorrections(rows, edits),
        controlValues: controlInputs
          .filter((c) => c.value !== null)
          .map((c) => ({ controlPointId: c.point.id, measuredValue: c.value as number })),
      }),
    onSuccess: refresh,
  })
  const reject = useMutation({ mutationFn: () => api.rejectChecklist(checklist.id, reason.trim()), onSuccess: refresh })

  const setEdit = (id: string, patch: RowEdit) => setEdits((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }))
  const resetEdit = (id: string) =>
    setEdits((prev) => {
      const next = { ...prev }
      delete next[id]
      return next
    })

  const stages: (RecipeStage | null)[] = recipe.stages.length > 0 ? recipe.stages : [null]

  return (
    <div className="review-layout">
      <section className="panel original" aria-label="Оригинал документа">
        <header>
          <h2>Оригинал</h2>
          <ul className="meta-list">
            <li>
              QR задания:{' '}
              {checklist.qrVerified ? <State tone="ok">совпадает</State> : <State tone="review">не найден</State>}
            </li>
            <li>
              Страниц: <b>{checklist.pageCount ?? '—'}</b>
            </li>
            <li>
              Уверенность OCR: <b>{checklist.ocrConfidence === null ? '—' : `${Math.round(checklist.ocrConfidence * 100)} %`}</b>
            </li>
          </ul>
        </header>
        <div className="viewer">
          <DocumentViewer load={loadFile} title={`Фото чек-листа задания №${order.orderNo}`} />
        </div>
      </section>

      <section aria-label="Распознанные строки">
        <div className="panel">
          <header>
            <h2>Распознанные строки</h2>
            <span className="small muted">
              Исправляйте только отмеченные строки. Остальные совпали с рецептурой.
            </span>
          </header>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">Компонент</th>
                  <th scope="col">Партия сырья</th>
                  <th scope="col" className="n">Факт, кг</th>
                  <th scope="col">Состояние и действие</th>
                </tr>
              </thead>
              {stages.map((stage) => {
                const stageRows = rows.filter((r) => (stage ? r.stageNo === stage.stageNo : true))
                const total = stage ? stageTotal(rows, stage.stageNo, stage.plannedQty) : null
                return (
                  <tbody key={stage?.id ?? 'all'}>
                    {stage && (
                      <tr className="stage-head">
                        <td colSpan={4}>
                          Стадия {stage.stageNo}. {stage.name}
                          {stage.plannedQty !== null && <span className="muted small"> · план {num(stage.plannedQty)} кг</span>}
                        </td>
                      </tr>
                    )}
                    {stageRows.map((row) => (
                      <LedgerRow
                        key={row.item.id}
                        row={row}
                        edit={edits[row.item.id]}
                        isEditing={editing === row.item.id}
                        lotRequired={recipe.requireRawMaterialLot}
                        onEdit={() => setEditing(row.item.id)}
                        onDone={() => setEditing(null)}
                        onCancel={() => {
                          resetEdit(row.item.id)
                          setEditing(null)
                        }}
                        onAccept={() => setEdit(row.item.id, { accepted: true })}
                        onChange={(patch) => setEdit(row.item.id, patch)}
                      />
                    ))}
                    {total && stage?.plannedQty ? (
                      <tr className="stage-total">
                        <td colSpan={4}>
                          Сумма стадии: {total.known ? `${num(total.sum)} кг` : 'не все веса известны'}
                          {total.deviation !== null && (
                            <>
                              {' '}
                              ({total.deviation >= 0 ? '+' : ''}
                              {num(total.deviation, 1)} % к плану)
                              {Math.abs(total.deviation) > 2 && (
                                <>
                                  {' '}
                                  <State tone="review">расхождение больше 2 %</State>
                                </>
                              )}
                            </>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                )
              })}
            </table>
          </div>
        </div>

        {controlInputs.length > 0 && (
          <div className="panel" style={{ marginTop: 16 }}>
            <header>
              <h2>Контрольные замеры</h2>
              <span className="small muted">Перепишите значения с бланка. Готовый продукт вне допуска уйдёт на карантин.</span>
            </header>
            <div className="body controls-grid">
              {controlInputs.map(({ point, value, within }) => {
                const id = `cp-${point.id}`
                return (
                  <div className="control-card" key={point.id}>
                    <label className="label" htmlFor={id}>
                      {point.parameterName}
                    </label>
                    <span className="small muted">
                      {point.stageNo ? `Стадия ${point.stageNo}` : 'Готовый продукт'} · допуск {range(point.minValue, point.maxValue, point.unit)}
                    </span>
                    <div className="actions">
                      <input
                        id={id}
                        className="input num"
                        inputMode="decimal"
                        aria-invalid={within === false}
                        defaultValue=""
                        onChange={(e) => setControls((prev) => ({ ...prev, [point.id]: parseNumber(e.target.value) }))}
                      />
                      {within === true && <State tone="ok">в допуске</State>}
                      {within === false && <State tone="out">вне допуска</State>}
                      {value === null && <State tone="missing">не введено</State>}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {confirm.isError && <div style={{ marginTop: 12 }}><ErrorNote error={confirm.error} /></div>}
        {reject.isError && <div style={{ marginTop: 12 }}><ErrorNote error={reject.error} /></div>}

        {rejecting && (
          <div className="panel" style={{ marginTop: 16 }}>
            <div className="body stack">
              <label className="field">
                Почему лист нельзя принять? Оператор распечатает и заполнит бланк заново.
                <textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              <div className="actions">
                <button className="btn danger" disabled={reason.trim().length < 3 || reject.isPending} onClick={() => reject.mutate()}>
                  Отклонить лист
                </button>
                <button className="btn quiet" onClick={() => setRejecting(false)}>
                  Отмена
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="confirm-bar" role="region" aria-label="Подтверждение">
          <div className="status-line" aria-live="polite">
            {ready.canConfirm ? (
              <State tone="ok">Всё проверено — можно подтвердить</State>
            ) : (
              ready.blockers.map((b) => (
                <State tone="review" key={b}>
                  {b}
                </State>
              ))
            )}
            {ready.warnings.map((w) => (
              <span key={w} className="small muted">
                {w}
              </span>
            ))}
          </div>
          <div className="actions">
            {!rejecting && (
              <button className="btn danger" onClick={() => setRejecting(true)}>
                Отклонить
              </button>
            )}
            <button className="btn primary large" disabled={!ready.canConfirm || confirm.isPending} onClick={() => confirm.mutate()}>
              {confirm.isPending ? 'Создаём партию…' : 'Подтвердить и создать партию'}
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}

function LedgerRow({ row, edit, isEditing, lotRequired, onEdit, onDone, onCancel, onAccept, onChange }: {
  row: ReviewRow
  edit: RowEdit | undefined
  isEditing: boolean
  lotRequired: boolean
  onEdit: () => void
  onDone: () => void
  onCancel: () => void
  onAccept: () => void
  onChange: (patch: RowEdit) => void
}) {
  const api = useApi()
  const ri = row.recipeItem
  const bounds = ri ? dosageBounds(ri.targetQty, ri.tolerancePlus, ri.toleranceMinus) : null
  const isIntermediate = ri?.sourceStageNo != null
  const needsLot = lotRequired && !isIntermediate
  const lots = useQuery({
    queryKey: ['lots', ri?.materialCode],
    queryFn: () => api.listLots({ materialCode: ri?.materialCode ?? '' }),
    enabled: isEditing && needsLot && !!ri?.materialCode,
  })
  const weightId = `w-${row.item.id}`
  const lotId = `l-${row.item.id}`
  const showMix = !!(ri?.mixSpeedNorm || ri?.mixMinutesNorm)

  return (
    <tr className={`row s-${row.state}${row.edited ? ' edited' : ''}`}>
      <td>
        <span className="material">{ri?.materialCode ?? row.item.materialNameRaw ?? 'Не распознан'}</span>
        {ri && (
          <span className="sub">
            план {num(ri.targetQty)} кг · допуск {bounds ? `${num(bounds.min)}–${num(bounds.max)}` : '—'}
          </span>
        )}
        {(ri?.mixSpeedNorm || ri?.mixMinutesNorm) && (
          <span className="sub">
            {ri?.mixSpeedNorm && `обороты: ${ri.mixSpeedNorm}`}
            {ri?.mixSpeedNorm && ri?.mixMinutesNorm ? '; ' : ''}
            {ri?.mixMinutesNorm && `перемешивание ${ri.mixMinutesNorm} мин`}
          </span>
        )}
        {row.item.reviewNote && row.pending && <span className="reason">{row.item.reviewNote}</span>}
      </td>
      <td className="lot">
        {isIntermediate ? (
          <span className="muted small">результат стадии {ri?.sourceStageNo}</span>
        ) : isEditing && needsLot ? (
          <>
            <label className="sr-only" htmlFor={lotId}>
              Партия сырья
            </label>
            <select
              id={lotId}
              className="input"
              value={row.lotId ?? ''}
              onChange={(e) => onChange({ rawMaterialBatchId: e.target.value || null })}
            >
              <option value="">— выберите —</option>
              {lots.data?.items.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.lotNo}
                  {l.expiry ? ` · до ${l.expiry}` : ''}
                </option>
              ))}
            </select>
          </>
        ) : (
          <>
            {row.item.batchNumberRaw ?? <span className="muted">не указана</span>}
            {needsLot && !row.lotId && <span className="reason">Партия не найдена на складе</span>}
          </>
        )}
      </td>
      <td className="n weight-cell">
        {isEditing ? (
          <div style={{ display: 'grid', gap: 6, justifyItems: 'end' }}>
            <label className="sr-only" htmlFor={weightId}>
              Фактический вес, кг
            </label>
            <input
              id={weightId}
              className="input num"
              inputMode="decimal"
              autoFocus
              defaultValue={row.weight === null ? '' : String(row.weight).replace('.', ',')}
              onChange={(e) => onChange({ weight: parseNumber(e.target.value) })}
              onKeyDown={(e) => e.key === 'Enter' && onDone()}
            />
            {showMix && (
              <span className="actions small">
                <input
                  aria-label="Обороты, об/мин"
                  placeholder="об/мин"
                  className="input num"
                  style={{ width: '5.5em' }}
                  inputMode="numeric"
                  defaultValue={edit?.mixSpeedActual ?? row.item.mixSpeedActual ?? ''}
                  onChange={(e) => onChange({ mixSpeedActual: parseNumber(e.target.value) })}
                />
                <input
                  aria-label="Перемешивание, мин"
                  placeholder="мин"
                  className="input num"
                  style={{ width: '4.5em' }}
                  inputMode="numeric"
                  defaultValue={edit?.mixMinutesActual ?? row.item.mixMinutesActual ?? ''}
                  onChange={(e) => onChange({ mixMinutesActual: parseNumber(e.target.value) })}
                />
              </span>
            )}
          </div>
        ) : (
          <>
            <span className="fact">{num(row.weight)}</span>
            {row.item.confidence !== null && row.item.confidence > 0 && !row.edited && (
              <span className="sub">распознано на {Math.round(row.item.confidence * 100)} %</span>
            )}
            {row.edited && <span className="sub">исправлено</span>}
          </>
        )}
      </td>
      <td className="state-cell">
        <State tone={row.state}>{STATE_LABEL[row.state]}</State>
        <div className="row-actions">
          {isEditing ? (
            <>
              <button className="btn small primary" onClick={onDone}>
                Готово
              </button>
              <button className="btn small quiet" onClick={onCancel}>
                Отмена
              </button>
            </>
          ) : (
            <>
              {row.pending && (row.state === 'review' || row.state === 'out') && (
                <button
                  className="btn small"
                  onClick={onAccept}
                  title={row.state === 'out' ? 'На бланке именно это значение: записать как отклонение' : 'Значение на фото совпадает с распознанным'}
                >
                  {row.state === 'out' ? 'Так на бланке' : 'Верно'}
                </button>
              )}
              <button className="btn small" onClick={onEdit}>
                Исправить
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  )
}
