import type { ChecklistCorrection, ChecklistItem, Recipe, RecipeControlPoint, RecipeItem } from '../api/types'
import { isDosageWithin, isWithinRange } from './tolerance'

/**
 * Состояние экрана проверки распознанного чек-листа. Чистые функции без React:
 * их легко тестировать, а компонент только рисует результат.
 */

/** Что оператор сделал со строкой: исправил значения или подтвердил распознанное как верное. */
export interface RowEdit {
  weight?: number | null
  rawMaterialBatchId?: string | null
  mixSpeedActual?: number | null
  mixMinutesActual?: number | null
  accepted?: boolean
}

export type RowState = 'ok' | 'review' | 'out' | 'missing'

export interface ReviewRow {
  item: ChecklistItem
  recipeItem: RecipeItem | undefined
  weight: number | null
  lotId: string | null
  state: RowState
  /** Строка требовала внимания и оператор её ещё не разобрал. */
  pending: boolean
  edited: boolean
  stageNo: number | null
}

export function buildRows(items: ChecklistItem[], recipe: Recipe | undefined, edits: Record<string, RowEdit>): ReviewRow[] {
  const byId = new Map((recipe?.items ?? []).map((ri) => [ri.id, ri]))
  const lotRequired = recipe?.requireRawMaterialLot ?? true

  return items.map((item) => {
    const edit = edits[item.id] ?? {}
    const recipeItem = item.recipeItemId ? byId.get(item.recipeItemId) : undefined
    const weight = edit.weight !== undefined ? edit.weight : item.weight
    const lotId = edit.rawMaterialBatchId !== undefined ? edit.rawMaterialBatchId : item.rawMaterialBatchId
    const edited =
      edit.weight !== undefined ||
      edit.rawMaterialBatchId !== undefined ||
      edit.mixSpeedActual !== undefined ||
      edit.mixMinutesActual !== undefined

    // Результат более ранней стадии — полуфабрикат, а не сырьё со склада: партии сырья у него нет.
    const needsLot = lotRequired && recipeItem?.sourceStageNo == null

    let state: RowState
    if (weight === null || item.materialId === null || (needsLot && !lotId)) {
      state = 'missing'
    } else if (recipeItem && !isDosageWithin(recipeItem.targetQty, weight, recipeItem.tolerancePlus, recipeItem.toleranceMinus)) {
      state = 'out'
    } else if (item.needsReview && !edited && !edit.accepted) {
      state = 'review'
    } else {
      state = 'ok'
    }

    const pending = item.needsReview && !edited && !edit.accepted
    return { item, recipeItem, weight, lotId, state, pending, edited, stageNo: recipeItem?.stageNo ?? null }
  })
}

export interface ControlInput {
  point: RecipeControlPoint
  value: number | null
  within: boolean | null
}

export function buildControls(recipe: Recipe | undefined, values: Record<string, number | null>): ControlInput[] {
  return (recipe?.controlPoints ?? []).map((point) => {
    const value = values[point.id] ?? null
    return { point, value, within: value === null ? null : isWithinRange(value, point.minValue, point.maxValue) }
  })
}

export interface ConfirmReadiness {
  canConfirm: boolean
  /** Причины, почему подтвердить пока нельзя — показываются оператору простыми словами. */
  blockers: string[]
  /** Отклонения, которые не мешают подтверждению, но будут записаны (вне допуска). */
  warnings: string[]
}

export function readiness(rows: ReviewRow[], controls: ControlInput[]): ConfirmReadiness {
  const blockers: string[] = []
  const warnings: string[] = []

  if (rows.length === 0) blockers.push('В листе нет строк. Проверьте распознавание или отклоните лист.')
  const pending = rows.filter((r) => r.pending).length
  if (pending > 0) blockers.push(`Осталось проверить строк: ${pending}`)
  const missing = rows.filter((r) => r.state === 'missing').length
  if (missing > 0) blockers.push(`Не заполнены значения в строках: ${missing}`)
  const missingControls = controls.filter((c) => c.value === null).length
  if (missingControls > 0) blockers.push(`Не введены контрольные замеры: ${missingControls}`)

  const out = rows.filter((r) => r.state === 'out').length
  if (out > 0) warnings.push(`Вне допуска строк: ${out}. Они будут записаны как отклонения.`)
  const outControls = controls.filter((c) => c.within === false).length
  if (outControls > 0) warnings.push(`Контрольные замеры вне допуска: ${outControls}.`)

  return { canConfirm: blockers.length === 0, blockers, warnings }
}

/**
 * Исправления для запроса confirm. Отправляются строки, которые оператор изменил или подтвердил;
 * сервер помечает их как проверенные и пишет изменения в журнал аудита.
 */
export function buildCorrections(rows: ReviewRow[], edits: Record<string, RowEdit>): ChecklistCorrection[] {
  return rows
    .filter((r) => r.edited || edits[r.item.id]?.accepted)
    .map((r) => {
      const e = edits[r.item.id] ?? {}
      const correction: ChecklistCorrection = { checklistItemId: r.item.id }
      if (e.weight !== undefined) correction.weight = e.weight
      if (e.rawMaterialBatchId !== undefined) correction.rawMaterialBatchId = e.rawMaterialBatchId
      if (e.mixSpeedActual !== undefined) correction.mixSpeedActual = e.mixSpeedActual
      if (e.mixMinutesActual !== undefined) correction.mixMinutesActual = e.mixMinutesActual
      return correction
    })
}

/** Сумма фактических масс по стадии и её отклонение от плана (для подсказки под таблицей стадии). */
export function stageTotal(rows: ReviewRow[], stageNo: number | null, planned: number | null) {
  const stageRows = rows.filter((r) => r.stageNo === stageNo)
  const known = stageRows.every((r) => r.weight !== null)
  const sum = stageRows.reduce((acc, r) => acc + (r.weight ?? 0), 0)
  const deviation = known && planned ? ((sum - planned) / planned) * 100 : null
  return { sum, known, deviation }
}
