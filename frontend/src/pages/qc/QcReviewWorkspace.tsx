import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useState } from 'react'
import { useApi } from '../../api/ContextHooks'
import type { QcChecklist, QualitySpec } from '../../api/types'
import { DocumentViewer } from '../../components/DocumentViewer'
import { ErrorNote } from '../../components/Query'
import { Norm } from './Norm'
import { State } from '../../components/State'
import { num, parseNumber } from '../../lib/format'
import {
  allowedValues,
  buildQcCorrections,
  buildQcRows,
  qcReadiness,
  type QcRow,
  type QcRowEdit,
} from '../../lib/qcReview'

const STATE_LABEL: Record<QcRow['state'], string> = {
  ok: 'Норма',
  review: 'Проверить',
  out: 'Вне нормы',
  missing: 'Нет значения',
  unmatched: 'Нет параметра',
}
const STATE_TONE: Record<QcRow['state'], 'ok' | 'review' | 'out' | 'missing'> = {
  ok: 'ok',
  review: 'review',
  out: 'out',
  missing: 'missing',
  unmatched: 'review',
}

/**
 * Проверка распознанного протокола качества: слева фото, справа параметры спецификации
 * с распознанными значениями. Перед подтверждением видно, чем оно закончится для партии.
 */
export function QcReviewWorkspace({ protocol, spec, labTechnician }: {
  protocol: QcChecklist
  spec: QualitySpec | undefined
  labTechnician: string
}) {
  const api = useApi()
  const queryClient = useQueryClient()
  const [edits, setEdits] = useState<Record<string, QcRowEdit>>({})
  const [editing, setEditing] = useState<string | null>(null)
  const [decisionNote, setDecisionNote] = useState('')
  const [actions, setActions] = useState('')
  const [secondSignatory, setSecondSignatory] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const rows = useMemo(() => buildQcRows(protocol.items, spec, edits), [protocol.items, spec, edits])
  const ready = qcReadiness(rows, !!spec)
  const willReject = ready.failed.length > 0
  const loadFile = useCallback(() => api.qcChecklistFile(protocol.id), [api, protocol.id])

  const refresh = (updated: QcChecklist) => {
    queryClient.setQueryData(['qc', 'one', updated.id], updated)
    for (const key of ['qc', 'batches', 'genealogy', 'spc']) queryClient.invalidateQueries({ queryKey: [key] })
  }
  const confirm = useMutation({
    mutationFn: () =>
      api.confirmQcChecklist(protocol.id, {
        labTechnician,
        corrections: buildQcCorrections(rows, edits),
        decisionNote: decisionNote.trim() || null,
        nonconformityActions: actions.trim() || null,
        secondSignatory: secondSignatory.trim() || null,
      }),
    onSuccess: refresh,
  })
  const reject = useMutation({ mutationFn: () => api.rejectQcChecklist(protocol.id, reason.trim()), onSuccess: refresh })

  const setEdit = (id: string, patch: QcRowEdit) => setEdits((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }))
  const resetEdit = (id: string) =>
    setEdits((prev) => {
      const next = { ...prev }
      delete next[id]
      return next
    })

  return (
    <div className="review-layout">
      <section className="panel original" aria-label="Оригинал протокола">
        <header>
          <h2>Оригинал</h2>
          <ul className="meta-list">
            <li>
              QR партии:{' '}
              {protocol.qrVerified ? <State tone="ok">совпадает</State> : <State tone="review">не найден</State>}
            </li>
            <li>
              Страниц: <b>{protocol.pageCount ?? '—'}</b>
            </li>
            <li>
              Уверенность OCR: <b>{protocol.ocrConfidence === null ? '—' : `${Math.round(protocol.ocrConfidence * 100)} %`}</b>
            </li>
          </ul>
        </header>
        <div className="viewer">
          <DocumentViewer load={loadFile} title={`Фото протокола качества партии ${protocol.batchNo}`} />
        </div>
      </section>

      <section aria-label="Результаты измерений">
        <div className="panel">
          <header>
            <h2>Результаты измерений</h2>
            <span className="small muted">Сверьте отмеченные строки с фото. Остальные распознаны уверенно и в норме.</span>
          </header>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">Параметр</th>
                  <th scope="col">Норма</th>
                  <th scope="col" className="n">Значение</th>
                  <th scope="col">Состояние и действие</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <QcLedgerRow
                    key={row.item.id}
                    row={row}
                    spec={spec}
                    usedSpecIds={new Set(rows.filter((r) => r !== row && r.spec).map((r) => r.spec!.id))}
                    isEditing={editing === row.item.id}
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
              </tbody>
            </table>
          </div>
        </div>

        <div className="panel" style={{ marginTop: 16 }}>
          <header>
            <h2>Заключение</h2>
            <span className="small muted">Перепишите с бланка, если заполнено. На статус партии не влияет: его определяют результаты.</span>
          </header>
          <div className="body stack" style={{ gap: 12 }}>
            <label className="field">
              Решение по партии
              <textarea className="input" rows={2} value={decisionNote} onChange={(e) => setDecisionNote(e.target.value)} />
            </label>
            {willReject && (
              <label className="field">
                Мероприятия по несоответствиям
                <textarea
                  className="input"
                  rows={2}
                  value={actions}
                  placeholder="Например: партию изолировать, пересмешать с корректировкой"
                  onChange={(e) => setActions(e.target.value)}
                />
              </label>
            )}
            <label className="field" style={{ maxWidth: 360 }}>
              Вторая подпись
              <input className="input" value={secondSignatory} onChange={(e) => setSecondSignatory(e.target.value)} />
            </label>
          </div>
        </div>

        {confirm.isError && <div style={{ marginTop: 12 }}><ErrorNote error={confirm.error} /></div>}
        {reject.isError && <div style={{ marginTop: 12 }}><ErrorNote error={reject.error} /></div>}

        {rejecting && (
          <div className="panel" style={{ marginTop: 16 }}>
            <div className="body stack">
              <label className="field">
                Почему протокол нельзя принять? Бланк заполнят и сфотографируют заново, партия останется на контроле.
                <textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              <div className="actions">
                <button className="btn danger" disabled={reason.trim().length < 3 || reject.isPending} onClick={() => reject.mutate()}>
                  Отклонить протокол
                </button>
                <button className="btn quiet" onClick={() => setRejecting(false)}>
                  Отмена
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="confirm-bar" role="region" aria-label="Подтверждение протокола">
          <div className="status-line" aria-live="polite">
            {ready.canConfirm ? (
              willReject ? (
                <State tone="out">Партия будет забракована: {ready.failed.join(', ')}</State>
              ) : (
                <State tone="ok">Все параметры в норме — партия будет выпущена</State>
              )
            ) : (
              ready.blockers.map((b) => (
                <State tone="review" key={b}>
                  {b}
                </State>
              ))
            )}
            {!ready.canConfirm && willReject && (
              <span className="small muted">Вне нормы: {ready.failed.join(', ')}. После подтверждения партия будет забракована.</span>
            )}
          </div>
          <div className="actions">
            {!rejecting && (
              <button className="btn danger" onClick={() => setRejecting(true)}>
                Отклонить
              </button>
            )}
            <button className="btn primary large" disabled={!ready.canConfirm || confirm.isPending} onClick={() => confirm.mutate()}>
              {confirm.isPending
                ? 'Подтверждаем…'
                : !ready.canConfirm
                  ? 'Подтвердить протокол'
                  : willReject
                    ? 'Подтвердить и забраковать партию'
                    : 'Подтвердить и выпустить партию'}
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}

function QcLedgerRow({ row, spec, usedSpecIds, isEditing, onEdit, onDone, onCancel, onAccept, onChange }: {
  row: QcRow
  spec: QualitySpec | undefined
  usedSpecIds: Set<string>
  isEditing: boolean
  onEdit: () => void
  onDone: () => void
  onCancel: () => void
  onAccept: () => void
  onChange: (patch: QcRowEdit) => void
}) {
  const valueId = `v-${row.item.id}`
  const paramId = `p-${row.item.id}`
  const listId = `allowed-${row.item.id}`
  const raw = row.item.parameterNameRaw
  const showRaw = raw && row.spec && raw !== row.spec.parameterName && raw !== row.spec.parameterCode
  const unit = row.spec?.unit ? ` ${row.spec.unit}` : ''

  return (
    <tr className={`row s-${row.state}${row.edited ? ' edited' : ''}`}>
      <td>
        {isEditing && !row.item.qualitySpecItemId && spec ? (
          <>
            <label className="sr-only" htmlFor={paramId}>Параметр спецификации</label>
            <select
              id={paramId}
              className="input"
              value={row.spec?.id ?? ''}
              onChange={(e) => onChange({ qualitySpecItemId: e.target.value || null })}
            >
              <option value="">— выберите параметр —</option>
              {spec.items.map((si) => (
                <option key={si.id} value={si.id}>
                  {si.parameterName}
                  {usedSpecIds.has(si.id) ? ' (уже есть в протоколе)' : ''}
                </option>
              ))}
            </select>
          </>
        ) : (
          <span className="material">{row.spec?.parameterName ?? 'Параметр не распознан'}</span>
        )}
        {(showRaw || !row.spec) && raw && <span className="sub">на бланке: «{raw}»</span>}
        {row.duplicate && <span className="reason">Этот параметр уже есть в другой строке.</span>}
        {row.item.reviewNote && row.pending && <span className="reason">{row.item.reviewNote}</span>}
      </td>
      <td className="norm"><Norm spec={row.spec} /></td>
      <td className="n weight-cell">
        {isEditing ? (
          <div style={{ display: 'grid', gap: 6, justifyItems: 'end' }}>
            <label className="sr-only" htmlFor={valueId}>Значение</label>
            {row.qualitative ? (
              <>
                <input
                  id={valueId}
                  className="input"
                  list={listId}
                  autoFocus
                  defaultValue={row.text ?? ''}
                  onChange={(e) => onChange({ textValue: e.target.value })}
                  onKeyDown={(e) => e.key === 'Enter' && onDone()}
                />
                <datalist id={listId}>
                  {allowedValues(row.spec).map((v) => (
                    <option key={v} value={v} />
                  ))}
                </datalist>
              </>
            ) : (
              <input
                id={valueId}
                className="input num"
                inputMode="decimal"
                autoFocus
                defaultValue={row.value === null ? '' : String(row.value).replace('.', ',')}
                onChange={(e) => onChange({ measuredValue: parseNumber(e.target.value) })}
                onKeyDown={(e) => e.key === 'Enter' && onDone()}
              />
            )}
          </div>
        ) : (
          <>
            {row.qualitative ? (
              <span className="text-value">{row.text || '—'}</span>
            ) : (
              <span className="fact">
                {num(row.value)}
                {row.value !== null && unit && <span className="unit">{unit}</span>}
              </span>
            )}
            {row.item.confidence !== null && row.item.confidence > 0 && !row.edited && (
              <span className="sub">распознано на {Math.round(row.item.confidence * 100)} %</span>
            )}
            {row.edited && <span className="sub">исправлено</span>}
          </>
        )}
      </td>
      <td className="state-cell">
        <State tone={STATE_TONE[row.state]}>{STATE_LABEL[row.state]}</State>
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
                  title={row.state === 'out' ? 'В протоколе именно это значение: записать как несоответствие' : 'Значение на фото совпадает с распознанным'}
                >
                  {row.state === 'out' ? 'Так в протоколе' : 'Верно'}
                </button>
              )}
              <button className="btn small" onClick={onEdit}>
                {row.state === 'unmatched' ? 'Выбрать параметр' : 'Исправить'}
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  )
}
