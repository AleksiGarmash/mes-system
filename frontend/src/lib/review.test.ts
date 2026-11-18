import { demoChecklists, demoRecipe } from '../api/demo/data'
import { buildControls, buildCorrections, buildRows, readiness, stageTotal } from './review'

const recipe = demoRecipe()
const checklist = demoChecklists()[0]
const allControls = { 'cp-ph-solution': 10.9, 'cp-ph-product': 10.3, 'cp-density': 1.121 }

describe('состояние строк', () => {
  it('распознанные строки получают правильные метки', () => {
    const rows = buildRows(checklist.items, recipe, {})
    const states = Object.fromEntries(rows.map((r) => [r.item.id, r.state]))
    expect(states).toEqual({ 'it-1': 'ok', 'it-2': 'review', 'it-3': 'out', 'it-4': 'ok', 'it-5': 'missing' })
  })

  it('исправленный вес пересчитывает допуск', () => {
    const rows = buildRows(checklist.items, recipe, { 'it-3': { weight: 61 } })
    const row = rows.find((r) => r.item.id === 'it-3')!
    expect(row.state).toBe('ok')
    expect(row.pending).toBe(false)
    expect(row.edited).toBe(true)
  })

  it('«Верно» снимает строку с проверки, но не меняет значение', () => {
    const rows = buildRows(checklist.items, recipe, { 'it-2': { accepted: true } })
    const row = rows.find((r) => r.item.id === 'it-2')!
    expect(row.pending).toBe(false)
    expect(row.weight).toBe(91.2)
  })

  it('промежуточный продукт не требует партии сырья, даже если рецептура требует партии', () => {
    expect(recipe.requireRawMaterialLot).toBe(true)
    const rows = buildRows(checklist.items, recipe, {})
    expect(rows.find((r) => r.item.id === 'it-4')!.state).toBe('ok')
  })

  it('обычный компонент без партии при обязательной партии — «нет значения»', () => {
    const items = checklist.items.map((i) => (i.id === 'it-1' ? { ...i, rawMaterialBatchId: null } : i))
    expect(buildRows(items, recipe, {}).find((r) => r.item.id === 'it-1')!.state).toBe('missing')
  })
})

describe('готовность к подтверждению', () => {
  it('нельзя подтвердить, пока есть непроверенные строки, пустые значения и замеры', () => {
    const rows = buildRows(checklist.items, recipe, {})
    const r = readiness(rows, buildControls(recipe, {}))
    expect(r.canConfirm).toBe(false)
    expect(r.blockers).toEqual([
      'Осталось проверить строк: 3',
      'Не заполнены значения в строках: 1',
      'Не введены контрольные замеры: 3',
    ])
  })

  it('после разбора всех строк и замеров подтвердить можно; отклонение остаётся предупреждением', () => {
    const edits = { 'it-2': { accepted: true }, 'it-3': { accepted: true }, 'it-5': { weight: 702 } }
    const rows = buildRows(checklist.items, recipe, edits)
    const r = readiness(rows, buildControls(recipe, allControls))
    expect(r.canConfirm).toBe(true)
    expect(r.warnings).toEqual(['Вне допуска строк: 1. Они будут записаны как отклонения.'])
  })

  it('пустой лист подтвердить нельзя', () => {
    expect(readiness([], []).blockers[0]).toMatch(/нет строк/)
  })

  it('замер вне допуска — предупреждение, не блокировка', () => {
    const r = readiness([], buildControls(recipe, { ...allControls, 'cp-ph-product': 11.2 }))
    expect(r.warnings).toContain('Контрольные замеры вне допуска: 1.')
  })
})

describe('исправления для сервера', () => {
  it('отправляются только тронутые строки и только изменённые поля', () => {
    const edits = { 'it-2': { accepted: true }, 'it-5': { weight: 702 } }
    const rows = buildRows(checklist.items, recipe, edits)
    expect(buildCorrections(rows, edits)).toEqual([
      { checklistItemId: 'it-2' },
      { checklistItemId: 'it-5', weight: 702 },
    ])
  })
})

it('сумма стадии и отклонение от плана', () => {
  const rows = buildRows(checklist.items, recipe, {})
  const total = stageTotal(rows, 1, 300)
  expect(total.known).toBe(true)
  expect(total.sum).toBeCloseTo(305.4)
  expect(total.deviation).toBeCloseTo(1.8)
  expect(stageTotal(rows, 2, 1000).known).toBe(false)
})
