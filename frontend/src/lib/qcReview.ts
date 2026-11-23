import type { QcChecklistItem, QcCorrection, QualitySpec, QualitySpecItem } from '../api/types'
import { isWithinRange } from './tolerance'

/**
 * Проверка распознанного протокола контроля качества. Чистые функции, как в review.ts:
 * компонент только рисует то, что здесь посчитано. Правила совпадают с QcChecklistService
 * на сервере; сервер при подтверждении проверяет всё заново.
 */

/** Что лаборант сделал со строкой: исправил значение, выбрал параметр или подтвердил распознанное. */
export interface QcRowEdit {
  measuredValue?: number | null
  textValue?: string | null
  qualitySpecItemId?: string | null
  accepted?: boolean
}

/** unmatched — строка не сопоставлена с параметром спецификации, результат не с чем сравнить. */
export type QcRowState = 'ok' | 'review' | 'out' | 'missing' | 'unmatched'

export interface QcRow {
  item: QcChecklistItem
  spec: QualitySpecItem | undefined
  qualitative: boolean
  value: number | null
  text: string | null
  within: boolean | null
  state: QcRowState
  /** Строка требовала внимания, а лаборант её ещё не разобрал. */
  pending: boolean
  edited: boolean
  /** Тот же параметр спецификации уже указан в другой строке. */
  duplicate: boolean
}

const normalize = (text: string) => text.trim().toLowerCase().replace(/\s+/g, ' ')

/** Допустимые значения качественного параметра: список через «;». */
export function allowedValues(spec: QualitySpecItem | undefined): string[] {
  return (spec?.allowedValues ?? '')
    .split(';')
    .map((v) => v.trim())
    .filter(Boolean)
}

/** Как RangeValidator.isAllowedValue: без учёта регистра и лишних пробелов. */
export function isAllowedValue(value: string | null, spec: QualitySpecItem | undefined): boolean {
  if (!value || !value.trim()) return false
  const v = normalize(value)
  return allowedValues(spec).some((a) => normalize(a) === v)
}

export function buildQcRows(items: QcChecklistItem[], spec: QualitySpec | undefined, edits: Record<string, QcRowEdit>): QcRow[] {
  const byId = new Map((spec?.items ?? []).map((si) => [si.id, si]))

  const rows = items.map((item): QcRow => {
    const edit = edits[item.id] ?? {}
    const specId = edit.qualitySpecItemId !== undefined ? edit.qualitySpecItemId : item.qualitySpecItemId
    const specItem = specId ? byId.get(specId) : undefined
    const qualitative = specItem?.parameterType === 'QUALITATIVE'
    const value = edit.measuredValue !== undefined ? edit.measuredValue : item.measuredValue
    const text = edit.textValue !== undefined ? edit.textValue : item.textValue
    const edited = edit.measuredValue !== undefined || edit.textValue !== undefined || edit.qualitySpecItemId !== undefined
    const pending = item.needsReview && !edited && !edit.accepted

    let within: boolean | null = null
    if (specItem) {
      if (qualitative) within = text && text.trim() ? isAllowedValue(text, specItem) : null
      else within = value !== null ? isWithinRange(value, specItem.minValue, specItem.maxValue) : null
    }
    const empty = qualitative ? !text || !text.trim() : value === null

    let state: QcRowState
    if (!specItem) state = 'unmatched'
    else if (empty) state = 'missing'
    else if (within === false) state = 'out'
    else if (pending) state = 'review'
    else state = 'ok'

    return { item, spec: specItem, qualitative, value, text, within, state, pending, edited, duplicate: false }
  })

  const counts = new Map<string, number>()
  for (const r of rows) if (r.spec) counts.set(r.spec.id, (counts.get(r.spec.id) ?? 0) + 1)
  for (const r of rows) r.duplicate = !!r.spec && (counts.get(r.spec.id) ?? 0) > 1

  // Порядок как на бланке: по номеру параметра в спецификации, несопоставленные строки в конце.
  const order = (r: QcRow) => (r.spec ? (r.spec.sequenceNo ?? 999) : 10_000)
  return rows.sort((a, b) => order(a) - order(b))
}

export interface QcReadiness {
  canConfirm: boolean
  /** Почему подтвердить пока нельзя, простыми словами. */
  blockers: string[]
  /** Параметры вне нормы: подтверждению не мешают, но партия будет забракована. */
  failed: string[]
}

export function qcReadiness(rows: QcRow[], hasSpec: boolean): QcReadiness {
  const blockers: string[] = []
  if (!hasSpec) blockers.push('Для рецептуры нет активной спецификации качества: результаты не с чем сравнить.')
  if (rows.length === 0) blockers.push('В протоколе нет строк. Проверьте распознавание или отклоните протокол.')
  const unmatched = rows.filter((r) => r.state === 'unmatched').length
  if (unmatched > 0) blockers.push(`Выберите параметр для строк: ${unmatched}`)
  const duplicates = new Set(rows.filter((r) => r.duplicate).map((r) => r.spec!.parameterName))
  if (duplicates.size > 0) blockers.push(`Параметр указан дважды: ${[...duplicates].join(', ')}`)
  // Пустые и несопоставленные строки считаются отдельно, чтобы одна строка не попадала в две причины.
  const pending = rows.filter((r) => r.pending && (r.state === 'review' || r.state === 'out')).length
  if (pending > 0) blockers.push(`Осталось проверить строк: ${pending}`)
  const missing = rows.filter((r) => r.state === 'missing').length
  if (missing > 0) blockers.push(`Не заполнены значения: ${missing}`)

  const failed = rows.filter((r) => r.state === 'out').map((r) => r.spec!.parameterName)
  return { canConfirm: blockers.length === 0, blockers, failed }
}

/** Отправляются строки, которые лаборант изменил или подтвердил; сервер пишет изменения в журнал аудита. */
export function buildQcCorrections(rows: QcRow[], edits: Record<string, QcRowEdit>): QcCorrection[] {
  return rows
    .filter((r) => r.edited || edits[r.item.id]?.accepted)
    .map((r) => {
      const e = edits[r.item.id] ?? {}
      const correction: QcCorrection = { checklistItemId: r.item.id }
      if (e.qualitySpecItemId !== undefined) correction.qualitySpecItemId = e.qualitySpecItemId
      if (e.measuredValue !== undefined) correction.measuredValue = e.measuredValue
      if (e.textValue !== undefined) correction.textValue = e.textValue?.trim() || null
      return correction
    })
}

/** Норма параметра для подписи: диапазон или список допустимых значений. */
export function normText(spec: QualitySpecItem | undefined, format: (n: number) => string): string {
  if (!spec) return '—'
  if (spec.parameterType === 'QUALITATIVE') return allowedValues(spec).join(' или ') || '—'
  const u = spec.unit ? ` ${spec.unit}` : ''
  if (spec.minValue !== null && spec.maxValue !== null) return `${format(spec.minValue)} – ${format(spec.maxValue)}${u}`
  if (spec.minValue !== null) return `≥ ${format(spec.minValue)}${u}`
  if (spec.maxValue !== null) return `≤ ${format(spec.maxValue)}${u}`
  return '—'
}
