import { demoQcChecklists, demoQualitySpec } from '../api/demo/data'
import type { QcChecklistItem } from '../api/types'
import { buildQcCorrections, buildQcRows, isAllowedValue, normText, qcReadiness } from './qcReview'

const spec = demoQualitySpec()
const items = demoQcChecklists()[0].items
const byParam = (rows: ReturnType<typeof buildQcRows>, id: string) => rows.find((r) => r.spec?.id === id)!

describe('проверка протокола качества', () => {
  it('состояние строки: норма, проверить, вне нормы, нет значения', () => {
    const rows = buildQcRows(items, spec, {})
    expect(byParam(rows, 'si-appearance').state).toBe('ok')
    expect(byParam(rows, 'si-density').state).toBe('review')
    expect(byParam(rows, 'si-ph').state).toBe('out')
    expect(byParam(rows, 'si-dry').state).toBe('missing')
  })

  it('строки идут в порядке спецификации, как на бланке', () => {
    const shuffled = [...items].reverse()
    expect(buildQcRows(shuffled, spec, {}).map((r) => r.spec?.id)).toEqual(['si-appearance', 'si-density', 'si-ph', 'si-dry'])
  })

  it('исправленное значение сразу пересчитывает норму', () => {
    const rows = buildQcRows(items, spec, { 'qi-3': { measuredValue: 10.38 }, 'qi-4': { measuredValue: 22.4 } })
    expect(byParam(rows, 'si-ph').state).toBe('ok')
    expect(byParam(rows, 'si-ph').edited).toBe(true)
    expect(byParam(rows, 'si-dry').state).toBe('ok')
  })

  it('качественный параметр сравнивается без учёта регистра и лишних пробелов', () => {
    const appearance = spec.items[0]
    expect(isAllowedValue('  Однородная   жидкость ', appearance)).toBe(true)
    expect(isAllowedValue('мутная', appearance)).toBe(false)
    expect(isAllowedValue('', appearance)).toBe(false)
  })

  it('подтверждение заблокировано, пока строки не разобраны', () => {
    const ready = qcReadiness(buildQcRows(items, spec, {}), true)
    expect(ready.canConfirm).toBe(false)
    expect(ready.blockers).toContain('Осталось проверить строк: 2')
    expect(ready.blockers).toContain('Не заполнены значения: 1')
  })

  it('«Так в протоколе» оставляет несоответствие: партия будет забракована', () => {
    const edits = { 'qi-2': { accepted: true }, 'qi-3': { accepted: true }, 'qi-4': { measuredValue: 22.4 } }
    const ready = qcReadiness(buildQcRows(items, spec, edits), true)
    expect(ready.canConfirm).toBe(true)
    expect(ready.failed).toEqual(['Водородный показатель pH'])
  })

  it('несопоставленная строка и повтор параметра блокируют подтверждение', () => {
    const extra: QcChecklistItem = { ...items[1], id: 'qi-x', qualitySpecItemId: null, parameterNameRaw: 'Вязк.', needsReview: true }
    let rows = buildQcRows([...items, extra], spec, { 'qi-2': { accepted: true }, 'qi-3': { measuredValue: 10.38 }, 'qi-4': { measuredValue: 22 } })
    expect(rows.at(-1)!.state).toBe('unmatched')
    expect(qcReadiness(rows, true).blockers).toEqual(['Выберите параметр для строк: 1'])

    rows = buildQcRows([...items, extra], spec, { 'qi-2': { accepted: true }, 'qi-3': { measuredValue: 10.38 }, 'qi-4': { measuredValue: 22 }, 'qi-x': { qualitySpecItemId: 'si-density' } })
    expect(qcReadiness(rows, true).blockers).toEqual(['Параметр указан дважды: Плотность'])
  })

  it('без спецификации подтвердить нельзя', () => {
    expect(qcReadiness(buildQcRows(items, undefined, {}), false).blockers[0]).toMatch(/нет активной спецификации/)
  })

  it('на сервер уходят только разобранные строки и только изменённые поля', () => {
    const edits = { 'qi-2': { accepted: true }, 'qi-3': { measuredValue: 10.38 }, 'qi-4': { measuredValue: 22.4 } }
    const corrections = buildQcCorrections(buildQcRows(items, spec, edits), edits)
    expect(corrections).toEqual([
      { checklistItemId: 'qi-2' },
      { checklistItemId: 'qi-3', measuredValue: 10.38 },
      { checklistItemId: 'qi-4', measuredValue: 22.4 },
    ])
  })

  it('норма для подписи: диапазон с единицей или список значений', () => {
    const fmt = (n: number) => String(n).replace('.', ',')
    expect(normText(spec.items[1], fmt)).toBe('1,05 – 1,15 г/см³')
    expect(normText(spec.items[0], fmt)).toBe('однородная жидкость или однородная жидкость без осадка')
  })
})
